(ns payment-orchestrator-clj.observability.log
  (:require [clojure.string :as string])
  (:import [java.time Instant]))

(def sensitive-keys #{"authorization" "stripe-signature" "secret" "token" "api-key" "password"})
(defn redact [value]
  (cond
    (map? value) (into {} (map (fn [[k v]] [(name k) (if (some #(string/includes? (string/lower-case (name k)) %) sensitive-keys) "[REDACTED]" (redact v))]) value))
    (sequential? value) (mapv redact value)
    :else value))
(defn event [fields]
  (prn (merge {:timestamp (str (Instant/now)) :level "INFO" :service "payment-orchestrator-clj"} (redact fields))))
