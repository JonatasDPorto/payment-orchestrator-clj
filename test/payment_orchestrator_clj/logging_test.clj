(ns payment-orchestrator-clj.logging-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.observability.log :as log]
            [payment-orchestrator-clj.observability.metrics :as metrics]
            [payment-orchestrator-clj.observability.trace :as trace])
  (:import [org.slf4j LoggerFactory]))

(deftest slf4j-has-a-runtime-provider
  (is (= "org.slf4j.simple.SimpleLoggerFactory"
         (.getName (class (LoggerFactory/getILoggerFactory))))))

(deftest secrets-are-redacted-before-logging
  (let [value (log/redact {:authorization "Bearer secret"
                           :ASAAS_API_KEY "asaas-api-key-must-not-appear"
                           :nested {:asaas_access_token "webhook-token-must-not-appear"}
                           :payment-id "safe"})]
    (is (= "[REDACTED]" (get value "authorization")))
    (is (= "[REDACTED]" (get value "ASAAS_API_KEY")))
    (is (= "[REDACTED]" (get-in value ["nested" "asaas_access_token"])))
    (is (not (.contains (pr-str value) "asaas-api-key-must-not-appear")))
    (is (not (.contains (pr-str value) "webhook-token-must-not-appear")))
    (is (= "safe" (get value "payment-id")))))

(deftest metrics-and-traces-capture-operational-work
  (let [registry (metrics/registry) spans (atom [])]
    (metrics/inc! registry "provider_timeout_total")
    (binding [trace/*spans* spans] (trace/with-span "provider.create" #(identity :ok)))
    (is (= 1 (get @registry "provider_timeout_total")))
    (is (= "provider.create" (:name (first @spans))))))
