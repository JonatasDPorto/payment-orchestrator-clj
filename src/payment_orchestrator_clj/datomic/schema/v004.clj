(ns payment-orchestrator-clj.datomic.schema.v004)

(def schema
  [{:db/ident :provider-event/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :provider-event/dedupe-key :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/value}
   {:db/ident :provider-event/provider :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :provider-event/external-id :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :provider-event/type :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :provider-event/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :provider-event/payload-sha256 :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :provider-event/provider-reference :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :provider-event/received-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}
   {:db/ident :provider-event/processed-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}
   {:db/ident :provider-event/payment :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :provider-event/error :db/valueType :db.type/string :db/cardinality :db.cardinality/one}])
