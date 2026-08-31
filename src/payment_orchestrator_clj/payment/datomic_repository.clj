(ns payment-orchestrator-clj.payment.datomic-repository
  "Datomic implementation of the payment repository boundary."
  (:require [datomic.client.api :as d]
            [payment-orchestrator-clj.payment.repository :as repository])
  (:import [java.time Instant]
           [java.util Date]))

(def payment-pull-pattern
  '[:payment/id :payment/customer-id :payment/amount :payment/currency
    :payment/method :payment/status :payment/created-at])

(def idempotency-pull-pattern
  '[:idempotency/request-hash
    {:idempotency/payment [:payment/id :payment/customer-id :payment/amount :payment/currency
                           :payment/method :payment/status :payment/created-at]}])

(declare datomic->domain)

(defn- require-created-at! [payment]
  (let [created-at (:payment/created-at payment)]
    (when-not (instance? Instant created-at)
      (throw (ex-info "Payment requires an Instant :payment/created-at before persistence"
                      {:error/code :payment.persistence/missing-created-at
                       :payment/id (:payment/id payment)})))
    created-at))

(defn domain->tx [payment]
  (let [created-at (require-created-at! payment)]
    {:payment/id (:payment/id payment)
     :payment/customer-id (:payment/customer-id payment)
     :payment/amount (:payment/amount payment)
     :payment/currency (:payment/currency payment)
     :payment/method (:payment/method payment)
     :payment/status (:payment/status payment)
     :payment/created-at (Date/from created-at)}))

(defn datomic->domain [entity]
  (when entity
    (-> entity
        (update :payment/created-at #(.toInstant ^Date %))
        (assoc :payment/events []))))

(defn- transaction-metadata [{:keys [request-id correlation-id source]}]
  (cond-> {:db/id "datomic.tx"}
    request-id (assoc :tx/request-id request-id)
    correlation-id (assoc :tx/correlation-id correlation-id)
    source (assoc :tx/source source)))

(defn- idempotency->tx [{:idempotency/keys [key request-hash created-at]} payment-eid]
  {:idempotency/key key
   :idempotency/request-hash request-hash
   :idempotency/payment payment-eid
   :idempotency/created-at (Date/from created-at)})

(defn- find-idempotency [connection key]
  (some-> (d/pull (d/db connection) idempotency-pull-pattern [:idempotency/key key])
          (update :idempotency/payment datomic->domain)))

(defrecord DatomicPaymentRepository [connection]
  repository/PaymentRepository
  (save-payment! [this payment]
    (repository/save-payment! this payment {:source :source/application}))
  (save-payment! [_ payment context]
    (d/transact connection {:tx-data [(domain->tx payment)
                                      (transaction-metadata context)]})
    payment)
  (create-payment-idempotently! [_ payment idempotency-record context]
    (try
      (let [payment-eid "payment"]
        (d/transact connection {:tx-data [(assoc (domain->tx payment) :db/id payment-eid)
                                          (idempotency->tx idempotency-record payment-eid)
                                          (transaction-metadata context)]}))
      {:outcome :created :payment payment}
      (catch clojure.lang.ExceptionInfo error
        (if-let [existing (find-idempotency connection (:idempotency/key idempotency-record))]
          (if (= (:idempotency/request-hash idempotency-record)
                 (:idempotency/request-hash existing))
            {:outcome :replayed :payment (:idempotency/payment existing)}
            {:outcome :conflict})
          (throw error)))))
  (record-provider-result! [_ payment provider-result context]
    (let [provider-payment {:provider-payment/id (java.util.UUID/randomUUID)
                            :provider-payment/payment [:payment/id (:payment/id payment)]
                            :provider-payment/provider (:provider provider-result)
                            :provider-payment/reference (:provider-payment/reference provider-result)
                            :provider-payment/status (:provider-payment/status provider-result)
                            :provider-payment/raw-status (:provider-payment/raw-status provider-result)
                            :provider-payment/created-at (Date/from (:provider-payment/created-at provider-result))}]
      (d/transact connection {:tx-data [(domain->tx payment)
                                        provider-payment
                                        (transaction-metadata context)]})
      payment))
  (find-payment [_ payment-id]
    (some-> (d/pull (d/db connection) payment-pull-pattern [:payment/id payment-id])
            datomic->domain)))

(defn new-repository [connection]
  (->DatomicPaymentRepository connection))
