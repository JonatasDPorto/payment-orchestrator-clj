(ns payment-orchestrator-clj.datomic.schema.v018)

(def schema
  [{:db/ident :provider-account/webhook-identity :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/value}
   {:db/ident :provider-payment/provider-account :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :provider-event/provider-account :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :provider-event/merchant-id :db/valueType :db.type/string :db/cardinality :db.cardinality/one}])
