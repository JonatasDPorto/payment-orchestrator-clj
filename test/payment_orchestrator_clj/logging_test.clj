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
  (let [registry (metrics/registry) spans (atom []) tracer (trace/test-tracer spans)
        context (trace/root-context "request-1" "correlation-1" nil)]
    (metrics/inc! registry "provider_timeout_total")
    (trace/with-span tracer context "payment.create" {} #(trace/with-span tracer % "provider.create" {:provider :stripe} (fn [_] :ok)))
    (is (= 1 (get @registry "provider_timeout_total")))
    (is (= #{"payment.create" "provider.create"} (set (map :name @spans))))
    (is (= "correlation-1" (:correlation-id (first @spans))))))
