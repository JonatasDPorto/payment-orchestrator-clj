(ns payment-orchestrator-clj.webhook.service
  (:require [payment-orchestrator-clj.payment.domain :as domain]
            [payment-orchestrator-clj.observability.metrics :as metrics]
            [payment-orchestrator-clj.ledger.service :as ledger]
            [payment-orchestrator-clj.payment.repository :as payments]
            [payment-orchestrator-clj.provider.asaas.webhook :as asaas]
            [payment-orchestrator-clj.provider.stripe.webhook :as stripe]
            [payment-orchestrator-clj.merchant.repository :as accounts]
            [payment-orchestrator-clj.webhook.repository :as events])
  (:import [java.time Instant]
           [java.util UUID]))

(defn- transaction-context [event-id]
  {:source :source/webhook :actor :actor/provider-webhook
   :reason :reason/provider-webhook :event-type :event/provider-webhook
   :request-id (str event-id) :correlation-id (str event-id)})

(defn- provider-account! [provider-accounts event]
  (when provider-accounts
    (let [identity (:provider-event/webhook-identity event)
        account (when (and provider-accounts (string? identity) (seq identity))
                  (accounts/find-provider-account-by-webhook-identity provider-accounts
                                                                     (:provider-event/provider event)
                                                                     identity))]
      (when-not account
        (throw (ex-info "Webhook provider account is unknown" {:error/code :webhook/unknown-provider-account})))
      (when-not (= :active (:provider-account/status account))
        (throw (ex-info "Webhook provider account is inactive" {:error/code :webhook/inactive-provider-account})))
      account)))

(defn- enqueue-event! [{:keys [provider-events provider-accounts clock id-generator metrics]} event payload-hash]
  (let [{:provider-event/keys [external-id] :as event} event]
    (when-not (and (string? external-id) (seq external-id))
      (throw (ex-info "Provider event is missing an identifier" {:error/code :webhook/invalid-event})))
    (let [account (provider-account! provider-accounts event)
          account-id (:provider-account/id account)
          merchant-id (get-in account [:provider-account/merchant :merchant/id])
          result (events/enqueue! provider-events
                                  (assoc event
                                         :provider-event/id (id-generator)
                                         :provider-event/provider-account-id account-id
                                         :provider-event/merchant-id merchant-id
                                         :provider-event/dedupe-key (str (name (:provider-event/provider event)) ":" (or account-id "legacy") ":" external-id)
                                         :provider-event/payload-sha256 payload-hash
                                         :provider-event/received-at (clock))
                                  {:source :source/webhook})]
      (when metrics
        (swap! metrics update "webhook_received_total" (fnil inc 0))
        (when (= :duplicate (:outcome result))
          (swap! metrics update "webhook_duplicate_total" (fnil inc 0))))
      result)))

(defn enqueue-stripe-event! [dependencies raw-body]
  (enqueue-event! dependencies (stripe/parse-event raw-body) (stripe/payload-hash raw-body)))

(defn enqueue-asaas-event! [dependencies raw-body]
  (enqueue-event! dependencies (asaas/parse-event raw-body) (asaas/payload-hash raw-body)))

(defn- transition-for-provider-event [payment target-status occurred-at]
  (cond
    (= target-status (:payment/status payment)) payment
    (and (= :payment.status/requires-action (:payment/status payment))
         (= :payment.status/paid target-status))
    (-> payment
        (domain/transition :payment.status/processing occurred-at)
        (domain/transition :payment.status/paid occurred-at))
    :else (domain/transition payment target-status occurred-at)))

(defn- apply-event! [{:keys [provider-events payments clock id-generator ledger] :as dependencies} event]
  (let [event-id (:provider-event/id event)
        context (transaction-context event-id)
        target-status (case (:provider-event/provider event)
                        :stripe (stripe/canonical-payment-status (:provider-event/type event))
                        :asaas (asaas/canonical-payment-status (:provider-event/type event))
                        nil)]
    (cond
      (nil? target-status) (events/mark-ignored! provider-events event-id context)
      :else (if-let [payment (events/payment-by-provider-reference provider-events
                                                                    (:provider-event/merchant-id event)
                                                                    (:provider-event/provider event)
                                                                    (get-in event [:provider-event/provider-account :provider-account/id])
                                                                    (:provider-event/provider-reference event))]
              (let [updated (transition-for-provider-event payment target-status (clock))]
                (when-not (= payment updated)
                  (payments/save-payment! payments updated (assoc context :reason :reason/provider-status-update
                                                                   :event-type :event/payment-status-changed)))
                (ledger/record-payment-settlement! {:ledger ledger :clock clock :id-generator id-generator}
                                                   updated context)
                (events/mark-processed! provider-events event-id (:payment/id payment) context))
              (events/record-processing-error! provider-events event-id "payment_not_found" context)))))

(defn process-pending! [dependencies]
  (doseq [event (events/pending-events (:provider-events dependencies))]
    (try
      (apply-event! dependencies event)
      (catch Exception _
        (events/record-processing-error! (:provider-events dependencies)
                                         (:provider-event/id event)
                                         "processing_failed"
                                         (transaction-context (:provider-event/id event)))))))

(defn dispatch! [dependencies]
  (future (process-pending! dependencies)))
