(ns payment-orchestrator-clj.observability.log
  (:require [clojure.string :as string])
  (:import [java.time Instant]))

(def sensitive-keys #{"authorization" "stripe-signature" "secret" "token" "api-key" "access-token" "password"})
(defn- sensitive-key? [key]
  (let [normalized (-> (name key) string/lower-case (string/replace "_" "-"))]
    (some #(string/includes? normalized %) sensitive-keys)))
(defn redact [value]
  (cond
    (map? value) (into {} (map (fn [[k v]] [(name k) (if (sensitive-key? k) "[REDACTED]" (redact v))]) value))
    (sequential? value) (mapv redact value)
    :else value))
(defn event [fields]
  (prn (merge {:timestamp (str (Instant/now)) :level "INFO" :service "payment-orchestrator-clj"} (redact fields))))
