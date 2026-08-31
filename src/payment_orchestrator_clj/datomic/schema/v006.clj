(ns payment-orchestrator-clj.datomic.schema.v006)

(def schema
  [{:db/ident :tx/actor :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :tx/reason :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :tx/event-type :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}])
