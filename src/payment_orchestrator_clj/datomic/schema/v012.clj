(ns payment-orchestrator-clj.datomic.schema.v012)

(def schema
  [{:db/ident :payment-action/document-url
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Canonical URL for a customer-facing payment document, such as a Boleto PDF."}])
