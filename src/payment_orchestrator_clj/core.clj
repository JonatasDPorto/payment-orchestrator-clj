(ns payment-orchestrator-clj.core
  "Application bootstrap."
  (:require [payment-orchestrator-clj.config :as config]
            [payment-orchestrator-clj.api.server :as server])
  (:import [org.slf4j LoggerFactory]))

(def ^:private logger (LoggerFactory/getLogger "payment-orchestrator-clj.bootstrap"))

(defn application-info []
  (select-keys (config/base-config)
               [:payment-orchestrator-clj/service-name :payment-orchestrator-clj/environment]))

(defn -main [& _]
  (let [{:payment-orchestrator-clj/keys [service-name environment]} (application-info)]
    (.info logger "Payment Orchestrator in Clojure API starting: service={} environment={}"
           (into-array Object [service-name (name environment)])))
  (server/start!))
