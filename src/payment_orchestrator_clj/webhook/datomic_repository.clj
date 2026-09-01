(ns payment-orchestrator-clj.webhook.datomic-repository
  (:require [datomic.client.api :as d]
            [payment-orchestrator-clj.payment.datomic-repository :as payments]
            [payment-orchestrator-clj.webhook.repository :as repository])
  (:import [java.time Instant]
           [java.util Date]))

(def event-pull-pattern
  '[:provider-event/id :provider-event/provider :provider-event/external-id
    :provider-event/type :provider-event/status :provider-event/payload-sha256
    :provider-event/provider-reference :provider-event/merchant-id :provider-event/received-at :provider-event/error
    {:provider-event/provider-account [:provider-account/id]}])

(defn- transaction-metadata [{:keys [request-id correlation-id actor source reason event-type]}]
  (cond-> {:db/id "datomic.tx"}
    request-id (assoc :tx/request-id request-id)
    correlation-id (assoc :tx/correlation-id correlation-id)
    actor (assoc :tx/actor actor)
    source (assoc :tx/source source)
    reason (assoc :tx/reason reason)
    event-type (assoc :tx/event-type event-type)))

(defn- datomic->event [event]
  (when event
    (update event :provider-event/received-at #(.toInstant ^Date %))))

(defn- event->tx [event]
  (cond-> {:provider-event/id (:provider-event/id event)
           :provider-event/dedupe-key (:provider-event/dedupe-key event)
           :provider-event/provider (:provider-event/provider event)
           :provider-event/external-id (:provider-event/external-id event)
           :provider-event/type (:provider-event/type event)
           :provider-event/status :provider-event.status/pending
           :provider-event/payload-sha256 (:provider-event/payload-sha256 event)
           :provider-event/provider-reference (:provider-event/provider-reference event)
           :provider-event/received-at (Date/from (:provider-event/received-at event))}
    (:provider-event/provider-account-id event)
    (assoc :provider-event/provider-account [:provider-account/id (:provider-event/provider-account-id event)])
    (:provider-event/merchant-id event)
    (assoc :provider-event/merchant-id (:provider-event/merchant-id event))))

(defrecord DatomicProviderEventRepository [connection]
  repository/ProviderEventRepository
  (enqueue! [_ event context]
    (try
      (d/transact connection {:tx-data [(event->tx event) (transaction-metadata context)]})
      {:outcome :accepted :event event}
      (catch clojure.lang.ExceptionInfo error
        (if (d/pull (d/db connection) [:provider-event/id]
                    [:provider-event/dedupe-key (:provider-event/dedupe-key event)])
          {:outcome :duplicate}
          (throw error)))))
  (pending-events [_]
    (->> (d/q '[:find (pull ?event pattern)
                :in $ pattern
                :where [?event :provider-event/status :provider-event.status/pending]]
              (d/db connection) event-pull-pattern)
         (mapv (comp datomic->event first))))
  (payment-by-provider-reference [this provider reference]
    (repository/payment-by-provider-reference this nil provider nil reference))
  (payment-by-provider-reference [_ merchant-id provider account-id reference]
    (let [query (if account-id
                               '[:find ?payment
                       :in $ ?merchant-id ?provider ?account-id ?reference
                       :where
                       [?payment :payment/merchant-id ?merchant-id]
                      [?provider-payment :provider-payment/provider ?provider]
                      [?provider-payment :provider-payment/provider-account ?account]
                      [?account :provider-account/id ?account-id]
                       [?provider-payment :provider-payment/reference ?reference]
                       [?provider-payment :provider-payment/payment ?payment]]
                               '[:find ?payment
                                 :in $ ?provider ?reference
                                 :where
                                 [?provider-payment :provider-payment/provider ?provider]
                                 [?provider-payment :provider-payment/reference ?reference]
                                 [?provider-payment :provider-payment/payment ?payment]])
          params (if account-id
                   [(d/db connection) merchant-id provider account-id reference]
                   [(d/db connection) provider reference])]
      (when-let [payment-eid (ffirst (apply d/q query params))]
      (payments/datomic->domain
       (d/pull (d/db connection)
               [:payment/id :payment/customer-id :payment/amount :payment/currency
                :payment/method :payment/status :payment/created-at]
               payment-eid)))))
  (mark-processed! [_ event-id payment-id context]
    (d/transact connection {:tx-data [{:db/id [:provider-event/id event-id]
                                       :provider-event/status :provider-event.status/processed
                                       :provider-event/payment [:payment/id payment-id]
                                       :provider-event/processed-at (Date/from (Instant/now))}
                                      (transaction-metadata context)]}))
  (mark-ignored! [_ event-id context]
    (d/transact connection {:tx-data [{:db/id [:provider-event/id event-id]
                                       :provider-event/status :provider-event.status/ignored
                                       :provider-event/processed-at (Date/from (Instant/now))}
                                      (transaction-metadata context)]}))
  (record-processing-error! [_ event-id error-code context]
    (d/transact connection {:tx-data [{:db/id [:provider-event/id event-id]
                                       :provider-event/error error-code}
                                      (transaction-metadata context)]})))

(defn new-repository [connection]
  (->DatomicProviderEventRepository connection))
