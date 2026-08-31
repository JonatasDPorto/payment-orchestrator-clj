(ns payment-orchestrator-clj.ledger.datomic-repository
  (:require [datomic.client.api :as d]
            [payment-orchestrator-clj.ledger.domain :as domain]
            [payment-orchestrator-clj.ledger.repository :as repository])
  (:import [java.util Date UUID]))

(def journal-pull-pattern
  '[:journal/id :journal/type :journal/dedupe-key :journal/created-at
    {:posting/_journal [:posting/id :posting/side :posting/amount :posting/currency
                        {:posting/account [:ledger-account/code :ledger-account/type :ledger-account/currency]}]}])

(defn- metadata [context]
  (cond-> {:db/id "datomic.tx"}
    (:request-id context) (assoc :tx/request-id (:request-id context))
    (:correlation-id context) (assoc :tx/correlation-id (:correlation-id context))
    (:actor context) (assoc :tx/actor (:actor context))
    (:source context) (assoc :tx/source (:source context))
    (:reason context) (assoc :tx/reason (:reason context))
    (:event-type context) (assoc :tx/event-type (:event-type context))))

(defn- datomic->journal [journal]
  (-> journal
      (update :journal/created-at #(.toInstant ^Date %))
      (assoc :journal/postings (vec (:posting/_journal journal)))
      (dissoc :posting/_journal)))

(defn- account-exists? [connection code]
  (some? (d/pull (d/db connection) [:ledger-account/code] [:ledger-account/code code])))

(defrecord DatomicLedgerRepository [connection]
  repository/LedgerRepository
  (ensure-accounts! [_]
    (doseq [account domain/default-accounts
            :when (not (account-exists? connection (:ledger-account/code account)))]
      (d/transact connection {:tx-data [(assoc account :ledger-account/id (UUID/randomUUID))]}))
    true)
  (record-journal! [this journal context]
    (domain/journal journal)
    (repository/ensure-accounts! this)
    (if (d/pull (d/db connection) [:journal/id] [:journal/dedupe-key (:journal/dedupe-key journal)])
      {:outcome :duplicate}
      (try
        (let [journal-eid "journal"
              journal-tx (-> (select-keys journal [:journal/id :journal/dedupe-key :journal/type])
                             (assoc :db/id journal-eid
                                    :journal/payment [:payment/id (:journal/payment journal)]
                                    :journal/created-at (Date/from (:journal/created-at journal))))
              postings (mapv #(-> (select-keys % [:posting/id :posting/side :posting/amount :posting/currency])
                                  (assoc :posting/journal journal-eid
                                         :posting/account [:ledger-account/code (:posting/account %)]))
                             (:journal/postings journal))]
          (d/transact connection {:tx-data (into [journal-tx (metadata context)] postings)})
          {:outcome :recorded :journal journal})
        (catch clojure.lang.ExceptionInfo error
          (if (d/pull (d/db connection) [:journal/id] [:journal/dedupe-key (:journal/dedupe-key journal)])
            {:outcome :duplicate}
            (throw error))))))
  (payment-journals [_ payment-id]
    (->> (d/q '[:find ?journal :in $ ?payment :where [?journal :journal/payment ?payment]]
              (d/db connection) [:payment/id payment-id])
         (mapv (comp datomic->journal #(d/pull (d/db connection) journal-pull-pattern (first %)))))))

(defn new-repository [connection] (->DatomicLedgerRepository connection))
