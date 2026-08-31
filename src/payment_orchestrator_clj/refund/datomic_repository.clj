(ns payment-orchestrator-clj.refund.datomic-repository
  (:require [datomic.client.api :as d]
            [payment-orchestrator-clj.refund.repository :as repository])
  (:import [java.time Instant] [java.util Date]))

(defn- metadata [context]
  (cond-> {:db/id "datomic.tx"}
    (:request-id context) (assoc :tx/request-id (:request-id context))
    (:correlation-id context) (assoc :tx/correlation-id (:correlation-id context))
    (:actor context) (assoc :tx/actor (:actor context))
    (:source context) (assoc :tx/source (:source context))
    (:reason context) (assoc :tx/reason (:reason context))
    (:event-type context) (assoc :tx/event-type (:event-type context))))

(defn- refund->tx [refund]
  (assoc refund :refund/created-at (Date/from (:refund/created-at refund))))

(defn- ->refund [refund]
  (update refund :refund/created-at #(.toInstant ^Date %)))

(defn- reconciliation->tx [reconciliation]
  (assoc reconciliation :refund-reconciliation/created-at
         (Date/from (:refund-reconciliation/created-at reconciliation))))

(defrecord DatomicRefundRepository [connection]
  repository/RefundRepository
  (save-refund! [_ refund context]
    (d/transact connection {:tx-data [(refund->tx refund) (metadata context)]})
    refund)
  (find-refund [_ refund-id]
    (some-> (d/pull (d/db connection) '[:refund/id :refund/payment-id :refund/amount :refund/status :refund/provider :refund/provider-reference :refund/created-at] [:refund/id refund-id])
            ->refund))
  (refunds-for-payment [_ payment-id]
    (->> (d/q '[:find (pull ?refund [:refund/id :refund/payment-id :refund/amount :refund/status
                                      :refund/provider :refund/provider-reference :refund/created-at])
                :in $ ?payment-id
                :where [?refund :refund/payment-id ?payment-id]]
              (d/db connection) payment-id)
         (map (comp ->refund first))
         (sort-by :refund/created-at)
         vec))
  (provider-payment-for [_ payment-id]
    (some-> (d/q '[:find (pull ?provider-payment [:provider-payment/provider :provider-payment/reference
                                                   :provider-payment/status :provider-payment/created-at])
                    :in $ ?payment-id
                    :where [?provider-payment :provider-payment/payment ?payment]
                           [?payment :payment/id ?payment-id]]
                  (d/db connection) payment-id)
            first first
            (update :provider-payment/created-at #(.toInstant ^Date %))))
  (record-reconciliation! [_ reconciliation context]
    (d/transact connection {:tx-data [(reconciliation->tx reconciliation) (metadata context)]})
    reconciliation))

(defn new-repository [connection] (->DatomicRefundRepository connection))
