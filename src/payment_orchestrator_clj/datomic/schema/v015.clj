(ns payment-orchestrator-clj.datomic.schema.v015)
(def schema
  [{:db/ident :dispute/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :dispute/payment-id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one}
   {:db/ident :dispute/amount :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :dispute/currency :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :dispute/reason :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :dispute/provider :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :dispute/provider-reference :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :dispute/provider-dedupe-key :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :dispute/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :dispute/created-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}])
