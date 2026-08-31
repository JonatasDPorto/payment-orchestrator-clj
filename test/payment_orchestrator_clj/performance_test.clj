(ns payment-orchestrator-clj.performance-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.performance :as performance]
            [payment-orchestrator-clj.provider.fake :as fake]
            [payment-orchestrator-clj.provider.port :as provider])
  (:import [java.util UUID]))

(deftest generated-dataset-is-deterministic-and-contains-unique-idempotency-keys
  (let [dataset (performance/generated-dataset 10)]
    (is (= dataset (performance/generated-dataset 10)))
    (is (= 10 (count (set (map :idempotency-key dataset)))))))

(deftest latency-summary-reports-percentiles-throughput-and-errors
  (let [summary (performance/summary [{:duration-nanos 1000000 :error? false}
                                      {:duration-nanos 2000000 :error? false}
                                      {:duration-nanos 3000000 :error? true}]
                                     6000000)]
    (is (= 2.0 (:p50-ms summary)))
    (is (= 3.0 (:p95-ms summary)))
    (is (= 3.0 (:p99-ms summary)))
    (is (= 1/3 (:error-rate summary)))))

(deftest slow-fake-provider-simulates-configured-latency
  (let [gateway (fake/new-gateway {:mode :slow-success :latency-ms 30})
        started (System/nanoTime)]
    (provider/create-payment! gateway {:payment/id (UUID/randomUUID)})
    (is (>= (/ (- (System/nanoTime) started) 1000000.0) 25.0))))
