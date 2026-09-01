(ns payment-orchestrator-clj.consumer-webhook.runner
  (:require [clojure.java.io :as io] [payment-orchestrator-clj.config :as config] [payment-orchestrator-clj.datomic.client :as datomic]
            [payment-orchestrator-clj.datomic.schema :as schema] [payment-orchestrator-clj.consumer-webhook.datomic-repository :as repository]
            [payment-orchestrator-clj.consumer-webhook.service :as service] [payment-orchestrator-clj.consumer-webhook.http :as http]))
(defn -main [& _]
  (let [cfg (config/base-config) db (:database cfg) client (datomic/new-client (assoc (dissoc db :database-name) :storage-dir (.getAbsolutePath (io/file (:storage-dir db))))) conn (datomic/create-connection! client (:database-name db))]
    (schema/install! conn)
    (service/deliver-pending! {:repository (repository/new-repository conn) :secret (or (System/getenv "PAYMENT_ORCHESTRATOR_WEBHOOK_SECRET") "") :sender (http/sender {})})))
