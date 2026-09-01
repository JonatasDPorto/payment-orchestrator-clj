(ns payment-orchestrator-clj.datomic.schema.v016)
(def schema
  [{:db/ident :consumer-delivery/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :consumer-delivery/dedupe-key :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :consumer-delivery/endpoint :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :consumer-delivery/event-id :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :consumer-delivery/event-type :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :consumer-delivery/payload :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :consumer-delivery/attempts :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :consumer-delivery/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :consumer-delivery/last-status :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :consumer-delivery/created-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}])
