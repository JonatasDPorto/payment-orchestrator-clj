(ns payment-orchestrator-clj.subscription.datomic-repository
  (:require [datomic.client.api :as d]
            [payment-orchestrator-clj.subscription.repository :as repository])
  (:import [java.time Instant] [java.util Date]))

(defn- ->date [instant] (when instant (Date/from instant)))
(defn- ->instant [date] (when date (.toInstant ^Date date)))
(def subscription-pattern '[:subscription/id :subscription/merchant-id :subscription/customer-id :subscription/amount :subscription/currency :subscription/interval :subscription/status :subscription/created-at])
(def invoice-pattern '[:invoice/id :invoice/subscription-id :invoice/payment-id :invoice/amount :invoice/currency :invoice/status :invoice/due-at :invoice/created-at])

(defrecord DatomicSubscriptionRepository [connection]
  repository/SubscriptionRepository
  (save-subscription! [_ subscription]
    (d/transact connection {:tx-data [(update subscription :subscription/created-at ->date)]}) subscription)
  (find-subscription [_ id]
    (some-> (d/pull (d/db connection) subscription-pattern [:subscription/id id])
            (update :subscription/created-at ->instant)))
  (save-invoice! [_ invoice]
    (d/transact connection {:tx-data [(-> invoice (update :invoice/due-at ->date) (update :invoice/created-at ->date))]}) invoice)
  (find-invoice [_ id]
    (some-> (d/pull (d/db connection) invoice-pattern [:invoice/id id])
            (update :invoice/due-at ->instant) (update :invoice/created-at ->instant))))

(defn new-repository [connection] (->DatomicSubscriptionRepository connection))
