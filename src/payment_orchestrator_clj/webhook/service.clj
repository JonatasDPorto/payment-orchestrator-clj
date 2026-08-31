(ns payment-orchestrator-clj.webhook.service
  (:require [payment-orchestrator-clj.payment.domain :as domain]
            [payment-orchestrator-clj.ledger.service :as ledger]
            [payment-orchestrator-clj.payment.repository :as payments]
            [payment-orchestrator-clj.provider.stripe.webhook :as stripe]
            [payment-orchestrator-clj.webhook.repository :as events])
  (:import [java.time Instant]
           [java.util UUID]))

(defn- transaction-context [event-id]
  {:source :source/webhook :actor :actor/provider-webhook
   :reason :reason/provider-webhook :event-type :event/provider-webhook
   :request-id (str event-id) :correlation-id (str event-id)})

(defn enqueue-stripe-event! [{:keys [provider-events clock id-generator]} raw-body]
  (let [{:provider-event/keys [external-id] :as event} (stripe/parse-event raw-body)]
    (when-not (and (string? external-id) (seq external-id))
      (throw (ex-info "Stripe event is missing an identifier" {:error/code :webhook/invalid-event})))
    (events/enqueue! provider-events
                     (assoc event
                            :provider-event/id (id-generator)
                            :provider-event/dedupe-key (str "stripe:" external-id)
                            :provider-event/payload-sha256 (stripe/payload-hash raw-body)
                            :provider-event/received-at (clock))
                     {:source :source/webhook})))

(defn- apply-event! [{:keys [provider-events payments clock id-generator ledger] :as dependencies} event]
  (let [event-id (:provider-event/id event)
        context (transaction-context event-id)
        target-status (stripe/canonical-payment-status (:provider-event/type event))]
    (cond
      (nil? target-status) (events/mark-ignored! provider-events event-id context)
      :else (if-let [payment (events/payment-by-provider-reference provider-events
                                                                    (:provider-event/provider event)
                                                                    (:provider-event/provider-reference event))]
              (let [updated (if (= target-status (:payment/status payment))
                              payment
                              (domain/transition payment target-status (clock)))]
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
