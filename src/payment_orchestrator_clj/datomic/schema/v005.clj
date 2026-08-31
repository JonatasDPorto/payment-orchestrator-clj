(ns payment-orchestrator-clj.datomic.schema.v005)

(def schema
  [{:db/ident :ledger-account/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :ledger-account/code :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/value}
   {:db/ident :ledger-account/type :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :ledger-account/currency :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :journal/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :journal/dedupe-key :db/valueType :db.type/string :db/cardinality :db.cardinality/one :db/unique :db.unique/value}
   {:db/ident :journal/payment :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :journal/type :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :journal/created-at :db/valueType :db.type/instant :db/cardinality :db.cardinality/one}
   {:db/ident :posting/id :db/valueType :db.type/uuid :db/cardinality :db.cardinality/one :db/unique :db.unique/identity}
   {:db/ident :posting/journal :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :posting/account :db/valueType :db.type/ref :db/cardinality :db.cardinality/one}
   {:db/ident :posting/side :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}
   {:db/ident :posting/amount :db/valueType :db.type/long :db/cardinality :db.cardinality/one}
   {:db/ident :posting/currency :db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}])
