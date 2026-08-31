(ns payment-orchestrator-clj.datomic.schema.v009)

(def schema
  [{:db/ident :payment/merchant-id :db/valueType :db.type/string :db/cardinality :db.cardinality/one
    :db/doc "Merchant ownership boundary for a payment."}
   {:db/ident :merchant/id :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :merchant/provider-account-id :db/valueType :db.type/string :db/cardinality :db.cardinality/one}])
