(ns payment-orchestrator-clj.datomic.schema.v017)

(def schema
  [{:db/ident :merchant/created-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}
   {:db/ident :provider-account/id :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :provider-account/merchant :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :provider-account/provider :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :provider-account/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :provider-account/secret-reference :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :provider-account/config :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :provider-account/created-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}
   {:db/ident :provider-account/updated-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}
   {:db/ident :merchant-provider-configuration/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :merchant-provider-configuration/merchant :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :merchant-provider-configuration/provider-account :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :merchant-provider-configuration/enabled :db/valueType :db.type/boolean :db/cardinality :db.cardinality/one}
   {:db/ident :merchant-provider-configuration/priority :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :merchant-provider-configuration/config :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :merchant-provider-configuration/created-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}
   {:db/ident :merchant-provider-configuration/updated-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}])
