(ns payment-orchestrator-clj.security-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.security :as security])
  (:import [java.io ByteArrayInputStream]))

(deftest rate-limiter-enforces-a-fixed-window-per-client-and-route
  (let [limiter (security/new-rate-limiter {:limit 2 :window-ms 1000})]
    (is (true? (security/allow! limiter [:post "/v1/payments" "client-a"] 100)))
    (is (true? (security/allow! limiter [:post "/v1/payments" "client-a"] 200)))
    (is (false? (security/allow! limiter [:post "/v1/payments" "client-a"] 300)))
    (is (true? (security/allow! limiter [:post "/v1/payments" "client-a"] 1000)))
    (is (true? (security/allow! limiter [:post "/v1/payments" "client-b"] 1000)))))

(deftest missing-api-key-configuration-does-not-expose-a-value
  (let [error (try
                (security/required-api-key nil)
                nil
                (catch clojure.lang.ExceptionInfo exception exception))]
    (is (= {:configuration-error :api-key-missing} (ex-data error)))
    (is (not (.contains (.getMessage error) "secret")))))

(deftest rate-limit-wrapper-returns-a-generic-response
  (let [limiter (security/new-rate-limiter {:limit 1 :window-ms 1000})
        handler (security/wrap-rate-limit (constantly {:status 200}) limiter (constantly 100))
        request {:request-method :post :uri "/v1/payments" :remote-addr "client-a"}]
    (is (= 200 (:status (handler request))))
    (is (= 429 (:status (handler request))))))

(deftest oversized-content-length-is-rejected-before-body-processing
  (let [handler (security/wrap-body-limit (constantly {:status 200}) 32)]
    (is (= 413 (:status (handler {:headers {"content-length" "33"}}))))
    (is (= 200 (:status (handler {:headers {"content-length" "32"}}))))
    (is (= 413 (:status (handler {:headers {}
                                   :body (ByteArrayInputStream. (byte-array 33))}))))))
