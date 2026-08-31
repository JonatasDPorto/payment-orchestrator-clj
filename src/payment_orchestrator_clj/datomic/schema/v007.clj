(ns payment-orchestrator-clj.datomic.schema.v007)

(def schema
  [{:db/ident :provider-operation/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :provider-operation/payment :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :provider-operation/provider :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :provider-operation/type :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :provider-operation/idempotency-key :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :provider-operation/status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :provider-operation/provider-reference :db/valueType :db.type/string :db/cardinality :db.cardinality/one}
   {:db/ident :provider-operation/started-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}
   {:db/ident :provider-operation/completed-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}
   {:db/ident :provider-operation/error-category :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :reconciliation/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :reconciliation/payment :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :reconciliation/provider :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :reconciliation/reason :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :reconciliation/local-status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :reconciliation/remote-status :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :reconciliation/result :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :reconciliation/created-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}])
