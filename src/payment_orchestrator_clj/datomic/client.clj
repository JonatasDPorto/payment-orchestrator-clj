(ns payment-orchestrator-clj.datomic.client
  "Lifecycle helpers for the Datomic Client API."
  (:require [datomic.client.api :as d]))

(defn new-client [config]
  (d/client config))

(defn create-connection! [client database-name]
  (d/create-database client {:db-name database-name})
  (d/connect client {:db-name database-name}))

(defn delete-database! [client database-name]
  (d/delete-database client {:db-name database-name}))
