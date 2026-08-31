(ns payment-orchestrator-clj.datomic.schema.v003)

(def schema
  [{:db/ident :provider-payment/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :provider-payment/payment :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :provider-payment/provider :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :provider-payment/reference :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :provider-payment/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :provider-payment/raw-status :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :provider-payment/created-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}])
