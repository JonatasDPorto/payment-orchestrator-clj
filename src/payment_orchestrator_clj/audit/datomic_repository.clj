(ns payment-orchestrator-clj.audit.datomic-repository
  (:require [datomic.client.api :as d]
            [payment-orchestrator-clj.audit.repository :as repository]
            [payment-orchestrator-clj.payment.datomic-repository :as payments])
  (:import [java.util Date]))

(def ^:private transaction-pull-pattern
  '[:db/txInstant :tx/request-id :tx/correlation-id :tx/actor :tx/source :tx/reason :tx/event-type])

(defn- instant [^Date value] (.toInstant value))

(defn- tx-context [database tx]
  (let [metadata (d/pull database transaction-pull-pattern tx)]
    {:audit/transaction tx
     :audit/at (instant (:db/txInstant metadata))
     :audit/request-id (:tx/request-id metadata)
     :audit/correlation-id (:tx/correlation-id metadata)
     :audit/actor (:tx/actor metadata)
     :audit/source (:tx/source metadata)
     :audit/reason (:tx/reason metadata)
     :audit/event-type (:tx/event-type metadata)}))

(defrecord DatomicPaymentAuditRepository [connection]
  repository/PaymentAuditRepository
  (payment-as-of [_ payment-id point]
    (some-> (d/pull (d/as-of (d/db connection) point)
                    payments/payment-pull-pattern
                    [:payment/id payment-id])
            payments/datomic->domain))
  (payment-history [_ payment-id]
    (let [database (d/db connection)
          history (d/history database)]
      (->> (d/q '[:find ?status ?tx ?added
                  :in $ ?payment-id
                  :where
                  [?payment :payment/id ?payment-id]
                  [?payment :payment/status ?status ?tx ?added]]
                history payment-id)
           (map (fn [[status tx added]]
                  (assoc (tx-context database tx)
                         :audit/status status
                         :audit/added? added)))
           (sort-by :audit/transaction)
           vec))))

(defn new-repository [connection] (->DatomicPaymentAuditRepository connection))
