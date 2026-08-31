(ns payment-orchestrator-clj.provider.stripe-errors-test
  (:require [clojure.test :refer [deftest is testing]]
            [payment-orchestrator-clj.provider.stripe.errors :as errors]))

(deftest stripe-response-statuses-map-to-canonical-errors
  (doseq [[status category retryable? known?]
          [[400 :provider.error/invalid-request false true]
           [401 :provider.error/authentication false true]
           [402 :provider.error/declined false true]
           [429 :provider.error/rate-limited true true]
           [500 :provider.error/unavailable true false]]]
    (testing (str status)
      (let [error (errors/response-error {:status status :body {:error {:code "test"}}})]
        (is (= category (:provider/error (ex-data error))))
        (is (= retryable? (:retryable? (ex-data error))))
        (is (= known? (:outcome-known? (ex-data error))))))))

(deftest stripe-timeout-has-an-unknown-outcome
  (let [error (errors/timeout-error)]
    (is (= :provider.error/timeout (:provider/error (ex-data error))))
    (is (false? (:outcome-known? (ex-data error))))))
