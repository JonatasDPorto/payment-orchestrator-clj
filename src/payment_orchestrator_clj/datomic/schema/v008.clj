(ns payment-orchestrator-clj.datomic.schema.v008)

(def schema
  [{:db/ident :relay/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :relay/consumer-name :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :relay/last-t :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :relay/updated-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}])
