(ns payment-orchestrator-clj.datomic.test-support
  (:require [payment-orchestrator-clj.config :as config]
            [payment-orchestrator-clj.datomic.client :as client]
            [payment-orchestrator-clj.datomic.schema :as schema])
  (:import [java.util UUID]))

(defn with-test-database [f]
  (let [database-config (:database (config/load-config "config/test.edn"))
        client (client/new-client (assoc database-config :system (str "payment-orchestrator-clj-test-" (UUID/randomUUID))))
        database-name (str "payment-" (UUID/randomUUID))
        connection (client/create-connection! client database-name)]
    (try
      (schema/install! connection)
      (f connection)
      (finally
        (client/delete-database! client database-name)))))
