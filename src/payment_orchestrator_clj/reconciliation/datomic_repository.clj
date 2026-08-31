(ns payment-orchestrator-clj.reconciliation.datomic-repository
  (:require [datomic.client.api :as d]
            [payment-orchestrator-clj.reconciliation.repository :as repository])
  (:import [java.time Instant] [java.util Date]))

(def ^:private operation-pull
  '[:provider-operation/id :provider-operation/provider :provider-operation/type
    :provider-operation/idempotency-key :provider-operation/status :provider-operation/provider-reference
    :provider-operation/started-at :provider-operation/error-category
    {:provider-operation/payment [:payment/id :payment/customer-id :payment/amount :payment/currency
                                  :payment/method :payment/status :payment/created-at]}])

(defn- metadata [context]
  (cond-> {:db/id "datomic.tx"}
    (:request-id context) (assoc :tx/request-id (:request-id context))
    (:correlation-id context) (assoc :tx/correlation-id (:correlation-id context))
    (:actor context) (assoc :tx/actor (:actor context))
    (:source context) (assoc :tx/source (:source context))
    (:reason context) (assoc :tx/reason (:reason context))
    (:event-type context) (assoc :tx/event-type (:event-type context))))

(defn- ->operation [operation]
  (-> operation
      (update :provider-operation/started-at #(.toInstant ^Date %))
      (update-in [:provider-operation/payment :payment/created-at] #(.toInstant ^Date %))))

(defrecord DatomicReconciliationRepository [connection]
  repository/ReconciliationRepository
  (start-operation! [_ operation context]
    (d/transact connection {:tx-data [(-> operation
                                       (assoc :provider-operation/payment [:payment/id (:provider-operation/payment operation)]
                                              :provider-operation/started-at (Date/from (:provider-operation/started-at operation))))
                                      (metadata context)]})
    operation)
  (complete-operation! [_ operation-id result context]
    (d/transact connection {:tx-data [(cond-> {:db/id [:provider-operation/id operation-id]
                                                :provider-operation/status (:provider-operation/status result)
                                                :provider-operation/completed-at (Date/from (:provider-operation/completed-at result))}
                                         (:provider-operation/provider result) (assoc :provider-operation/provider (:provider-operation/provider result))
                                         (:provider-operation/provider-reference result) (assoc :provider-operation/provider-reference (:provider-operation/provider-reference result))
                                         (:provider-operation/error-category result) (assoc :provider-operation/error-category (:provider-operation/error-category result)))
                                      (metadata context)]}))
  (unresolved-operations [_]
    (->> (d/q '[:find (pull ?operation pattern)
                :in $ pattern
                :where [?operation :provider-operation/status :provider-operation.status/outcome-unknown]]
              (d/db connection) operation-pull)
         (mapv (comp ->operation first))))
  (record-reconciliation! [_ reconciliation context]
    (d/transact connection {:tx-data [(-> reconciliation
                                       (assoc :reconciliation/payment [:payment/id (:reconciliation/payment reconciliation)]
                                              :reconciliation/created-at (Date/from (:reconciliation/created-at reconciliation))))
                                      (metadata context)]})
    reconciliation))

(defn new-repository [connection] (->DatomicReconciliationRepository connection))
