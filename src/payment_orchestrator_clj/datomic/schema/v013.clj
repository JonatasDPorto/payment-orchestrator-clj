(ns payment-orchestrator-clj.datomic.schema.v013)

(def schema
  [{:db/ident :subscription/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :subscription/merchant-id :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :subscription/customer-id :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :subscription/amount :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :subscription/currency :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :subscription/interval :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :subscription/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :subscription/created-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}
   {:db/ident :invoice/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :invoice/subscription-id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one}
   {:db/ident :invoice/payment-id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one}
   {:db/ident :invoice/amount :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :invoice/currency :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :invoice/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :invoice/due-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}
   {:db/ident :invoice/created-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}])
