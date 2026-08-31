(ns payment-orchestrator-clj.datomic.schema.v011)

(def schema
  [{:db/ident :payment-action/qr-code-url
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Canonical URL for rendering a Pix QR code, when the provider supplies one."}
   {:db/ident :payment-action/hosted-instructions-url
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Canonical hosted Pix instructions URL, when the provider supplies one."}])
