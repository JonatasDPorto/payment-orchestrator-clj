(ns payment-orchestrator-clj.api.server
  "Runtime composition for the HTTP API."
  (:require [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [payment-orchestrator-clj.api.routes :as routes]
            [payment-orchestrator-clj.config :as config]
            [payment-orchestrator-clj.datomic.client :as datomic-client]
            [payment-orchestrator-clj.datomic.schema :as schema]
            [payment-orchestrator-clj.payment.datomic-repository :as datomic-repository]
            [payment-orchestrator-clj.provider.fake :as fake])
  (:import [java.time Instant]
           [java.util UUID]))

(defn- normalize-client-config [database-config]
  (update (dissoc database-config :database-name) :storage-dir
          #(if (string? %) (.getAbsolutePath (io/file %)) %)))

(defn application-dependencies [application-config]
  (let [database-config (:database application-config)
        client (datomic-client/new-client (normalize-client-config database-config))
        connection (datomic-client/create-connection! client (:database-name database-config))]
    (schema/install! connection)
    {:payments (datomic-repository/new-repository connection)
     :gateway (fake/new-gateway (get-in application-config [:payments :fake]))
     :clock #(Instant/now)
     :id-generator #(UUID/randomUUID)}))

(defn start! []
  (let [application-config (config/base-config)
        dependencies (application-dependencies application-config)]
    (jetty/run-jetty (routes/handler dependencies)
                     {:port (get-in application-config [:http :port]) :join? true})))
