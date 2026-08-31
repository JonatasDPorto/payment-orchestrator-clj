(ns payment-orchestrator-clj.datomic.schema.v014)

(def schema
  [{:db/ident :refund/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :refund/payment-id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one}
   {:db/ident :refund/amount :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :refund/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :refund/provider :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :refund/provider-reference :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :refund/created-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}
   {:db/ident :refund-reconciliation/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :refund-reconciliation/payment-id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one}
   {:db/ident :refund-reconciliation/local-amount :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :refund-reconciliation/remote-amount :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :refund-reconciliation/result :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :refund-reconciliation/created-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}])
