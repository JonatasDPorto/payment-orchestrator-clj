(ns payment-orchestrator-clj.datomic.schema.v010)

(def schema
  [{:db/ident :payment/action
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/isComponent true
    :db/doc "Canonical customer action required to complete a payment."}
   {:db/ident :payment-action/type
    :db/valueType :db.type/keyword
    :db/cardinality :db.cardinality/one}
   {:db/ident :payment-action/payload
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Canonical Pix copy-and-paste payload; never a provider raw object."}
   {:db/ident :payment-action/expires-at
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one}])
