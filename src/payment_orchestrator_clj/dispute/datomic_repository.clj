(ns payment-orchestrator-clj.dispute.datomic-repository
  (:require [datomic.client.api :as d] [payment-orchestrator-clj.dispute.repository :as repository])
  (:import [java.util Date]))
(def pattern '[:dispute/id :dispute/payment-id :dispute/amount :dispute/currency :dispute/reason :dispute/provider :dispute/provider-reference :dispute/status :dispute/created-at])
(defn- ->domain [x] (some-> x (update :dispute/created-at #(.toInstant ^Date %))))
(defrecord DatomicDisputeRepository [connection]
  repository/DisputeRepository
  (save-dispute! [_ dispute context]
    (d/transact connection {:tx-data [(assoc dispute :dispute/provider-dedupe-key (str (name (:dispute/provider dispute)) ":" (:dispute/provider-reference dispute)) :dispute/created-at (Date/from (:dispute/created-at dispute)))
                                      (cond-> {:db/id "datomic.tx"} (:source context) (assoc :tx/source (:source context)))]}) dispute)
  (find-dispute [_ id] (->domain (d/pull (d/db connection) pattern [:dispute/id id])))
  (find-dispute-by-provider-reference [_ provider reference] (->domain (d/pull (d/db connection) pattern [:dispute/provider-dedupe-key (str (name provider) ":" reference)]))))
(defn new-repository [connection] (->DatomicDisputeRepository connection))
