(ns payment-orchestrator-clj.datomic.schema
  (:require [datomic.client.api :as d]
            [payment-orchestrator-clj.datomic.schema.v001 :as v001]
            [payment-orchestrator-clj.datomic.schema.v002 :as v002]
            [payment-orchestrator-clj.datomic.schema.v003 :as v003]
            [payment-orchestrator-clj.datomic.schema.v004 :as v004]
            [payment-orchestrator-clj.datomic.schema.v005 :as v005]))

(def schema (into [] (concat v001/schema v002/schema v003/schema v004/schema v005/schema)))

(defn install! [connection]
  (d/transact connection {:tx-data schema}))
