(ns payment-orchestrator-clj.event.relay-runner
  "One-shot relay process. Run it repeatedly from an orchestrator or scheduler."
  (:require [clojure.java.io :as io]
            [payment-orchestrator-clj.config :as config]
            [payment-orchestrator-clj.datomic.client :as datomic]
            [payment-orchestrator-clj.datomic.schema :as schema]
            [payment-orchestrator-clj.event.kafka :as kafka]
            [payment-orchestrator-clj.event.relay :as relay])
  (:import [java.time Instant]))

(defn- database-config []
  (let [database (:database (config/base-config))]
    (update (dissoc database :database-name) :storage-dir
            #(if (string? %) (.getAbsolutePath (io/file %)) %))))

(defn -main [& _]
  (let [application-config (config/base-config)
        database (:database application-config)
        client (datomic/new-client (database-config))
        connection (datomic/create-connection! client (:database-name database))
        bootstrap-servers (or (System/getenv "KAFKA_BOOTSTRAP_SERVERS") "localhost:9092")
        producer (kafka/new-producer {:bootstrap-servers bootstrap-servers})]
    (schema/install! connection)
    (relay/run-once! {:connection connection :producer producer :clock #(Instant/now)})))
