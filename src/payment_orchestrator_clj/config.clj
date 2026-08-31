(ns payment-orchestrator-clj.config
  "Configuration loading for the local application bootstrap."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn load-config
  "Loads an EDN configuration resource and fails clearly when it is unavailable."
  [resource-name]
  (if-let [resource (io/resource resource-name)]
    (edn/read-string (slurp resource))
    (throw (ex-info "Configuration resource not found"
                    {:resource resource-name}))))

(defn base-config []
  (load-config "config/base.edn"))
