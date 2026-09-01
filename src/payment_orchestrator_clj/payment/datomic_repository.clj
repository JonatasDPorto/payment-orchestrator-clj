(ns payment-orchestrator-clj.payment.datomic-repository
  "Datomic implementation of the payment repository boundary."
  (:require [datomic.client.api :as d]
            [payment-orchestrator-clj.payment.repository :as repository])
  (:import [java.time Instant]
           [java.util Date]))

(def payment-pull-pattern
  '[:payment/id :payment/merchant-id :payment/customer-id :payment/amount :payment/currency
    :payment/method :payment/status :payment/created-at
    {:payment/action [:payment-action/type :payment-action/payload :payment-action/qr-code-url :payment-action/document-url
                      :payment-action/hosted-instructions-url :payment-action/expires-at]}])

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
    (cond-> {:payment/id (:payment/id payment)
             :payment/merchant-id (or (:payment/merchant-id payment) "default")
             :payment/customer-id (:payment/customer-id payment)
             :payment/amount (:payment/amount payment)
             :payment/currency (:payment/currency payment)
             :payment/method (:payment/method payment)
             :payment/status (:payment/status payment)
             :payment/created-at (Date/from created-at)}
      (:payment/action payment)
      (assoc :payment/action (cond-> (select-keys (:payment/action payment)
                                                 [:payment-action/type :payment-action/payload
                                                 :payment-action/qr-code-url :payment-action/hosted-instructions-url
                                                 :payment-action/document-url])
                              (:payment-action/expires-at (:payment/action payment))
                              (assoc :payment-action/expires-at
                                     (Date/from (:payment-action/expires-at (:payment/action payment)))))))))

(defn datomic->domain [entity]
  (when entity
    (let [action (:payment/action entity)]
      (cond-> (-> entity
                  (update :payment/created-at #(.toInstant ^Date %))
                  (update-in [:payment/action :payment-action/expires-at]
                             #(when % (.toInstant ^Date %)))
                  (assoc :payment/events []))
        (nil? (:payment-action/type action)) (dissoc :payment/action)))))

(defn- transaction-metadata [{:keys [request-id correlation-id actor source reason event-type]}]
  (cond-> {:db/id "datomic.tx"}
    request-id (assoc :tx/request-id request-id)
    correlation-id (assoc :tx/correlation-id correlation-id)
    actor (assoc :tx/actor actor)
    source (assoc :tx/source source)
    reason (assoc :tx/reason reason)
    event-type (assoc :tx/event-type event-type)))

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
                            :provider-payment/created-at (Date/from (:provider-payment/created-at provider-result))}
            provider-payment (cond-> provider-payment
                               (:provider-account/id provider-result)
                               (assoc :provider-payment/provider-account [:provider-account/id (:provider-account/id provider-result)]))]
      (d/transact connection {:tx-data [(domain->tx payment)
                                        provider-payment
                                        (transaction-metadata context)]})
      payment))
  (find-payment [_ payment-id]
    (some-> (d/pull (d/db connection) payment-pull-pattern [:payment/id payment-id])
            datomic->domain)))

(defn new-repository [connection]
  (->DatomicPaymentRepository connection))
