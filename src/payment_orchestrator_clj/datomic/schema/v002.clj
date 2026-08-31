(ns payment-orchestrator-clj.datomic.schema.v002)

(def schema
  [{:db/ident :idempotency/key
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/value
    :db/doc "Consumer idempotency key for payment creation."}
   {:db/ident :idempotency/request-hash
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one}
   {:db/ident :idempotency/payment
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one}
   {:db/ident :idempotency/created-at
    :db/valueType :db.type/instant
    :db/cardinality :db.cardinality/one}])
