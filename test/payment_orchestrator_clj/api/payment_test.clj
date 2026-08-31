(ns payment-orchestrator-clj.api.payment-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.api.routes :as routes]
            [payment-orchestrator-clj.audit.repository :as audit]
            [payment-orchestrator-clj.provider.fake :as fake]
            [payment-orchestrator-clj.security :as security]
            [payment-orchestrator-clj.payment.repository :as repository])
  (:import [java.io ByteArrayInputStream]
           [java.time Instant]
           [java.util UUID]))

(defrecord InMemoryPayments [payments idempotency]
  repository/PaymentRepository
  (save-payment! [this payment]
    (repository/save-payment! this payment {}))
  (save-payment! [_ payment _]
    (swap! payments assoc (:payment/id payment) payment)
    payment)
  (create-payment-idempotently! [_ payment record _]
    (let [key (:idempotency/key record)
          existing (get @idempotency key)]
      (cond
        (nil? existing) (do (swap! payments assoc (:payment/id payment) payment)
                            (swap! idempotency assoc key {:request-hash (:idempotency/request-hash record)
                                                         :payment payment})
                            {:outcome :created :payment payment})
        (= (:idempotency/request-hash record) (:request-hash existing))
        {:outcome :replayed :payment (:payment existing)}
        :else {:outcome :conflict})))
  (record-provider-result! [_ payment _ _]
    (swap! payments assoc (:payment/id payment) payment)
    payment)
  (find-payment [_ payment-id]
    (get @payments payment-id)))

(defrecord InMemoryAudit [as-of history]
  audit/PaymentAuditRepository
  (payment-as-of [_ payment-id _] (get @as-of payment-id))
  (payment-history [_ payment-id] (get @history payment-id [])))

(defn- api
  ([] (api {}))
  ([overrides]
   (routes/handler (merge {:payments (->InMemoryPayments (atom {}) (atom {}))
                           :api-key "test-api-key"
                           :audit (->InMemoryAudit (atom {}) (atom {}))
                           :gateway (fake/new-gateway {:mode :always-success})
                           :clock #(Instant/parse "2026-08-30T12:00:00Z")
                           :id-generator #(UUID/fromString "fc1b6fa6-1ed5-4211-a9fd-fb4e1dfefa0d")}
                          overrides))))

(defn- request [method uri body & [key]]
  {:request-method method
   :uri uri
   :headers (cond-> {"content-type" "application/json" "x-api-key" "test-api-key"}
              (= method :post) (assoc "idempotency-key" (or key "test-key")))
   :body (ByteArrayInputStream. (.getBytes body "UTF-8"))})

(defn- response-body [response]
  (json/read-str (:body response) :key-fn keyword))

(deftest missing-api-key-is-rejected-before-the-payment-handler
  (let [response ((api) {:request-method :post
                          :uri "/v1/payments"
                          :headers {"content-type" "application/json"
                                    "idempotency-key" "test-key"}
                          :body (ByteArrayInputStream. (.getBytes "{}" "UTF-8"))})]
    (is (= 401 (:status response)))
    (is (= "unauthorized" (get-in (response-body response) [:error :code])))))

(deftest invalid-api-key-is-rejected-without-reflecting-the-secret
  (let [secret "not-a-real-secret"
        response ((api) {:request-method :get
                          :uri "/v1/payments/fc1b6fa6-1ed5-4211-a9fd-fb4e1dfefa0d"
                          :headers {"authorization" (str "Bearer " secret)}
                          :body (ByteArrayInputStream. (byte-array 0))})]
    (is (= 401 (:status response)))
    (is (not (.contains (:body response) secret)))))

(deftest bearer-api-key-is-accepted
  (let [response ((api) {:request-method :get
                          :uri "/v1/payments/fc1b6fa6-1ed5-4211-a9fd-fb4e1dfefa0d"
                          :headers {"authorization" "Bearer test-api-key"}
                          :body (ByteArrayInputStream. (byte-array 0))})]
    (is (= 404 (:status response)))))

(deftest oversized-body-is-rejected-at-the-http-boundary
  (let [response ((api {:max-request-body-bytes 4})
                  (request :post "/v1/payments" "12345"))]
    (is (= 413 (:status response)))
    (is (= "payload_too_large" (get-in (response-body response) [:error :code])))))

(deftest rate-limit-is-enforced-at-the-http-boundary
  (let [handler (api {:rate-limiter (security/new-rate-limiter {:limit 1 :window-ms 60000})})
        first-response (handler (request :get "/v1/payments/fc1b6fa6-1ed5-4211-a9fd-fb4e1dfefa0d" ""))
        second-response (handler (request :get "/v1/payments/fc1b6fa6-1ed5-4211-a9fd-fb4e1dfefa0d" ""))]
    (is (= 404 (:status first-response)))
    (is (= 429 (:status second-response)))
    (is (= "rate_limited" (get-in (response-body second-response) [:error :code])))))

(deftest post-valid-payment-returns-created-payment
  (let [response ((api) (request :post "/v1/payments"
                                  "{\"customerId\":\"cust-123\",\"amount\":12990,\"currency\":\"BRL\",\"method\":\"card\"}"))]
    (is (= 201 (:status response)))
    (is (= {:id "fc1b6fa6-1ed5-4211-a9fd-fb4e1dfefa0d" :status "processing" :amount 12990 :currency "BRL"}
           (response-body response)))))

(deftest get-existing-payment-returns-it
  (let [handler (api)
        created (handler (request :post "/v1/payments"
                                  "{\"customerId\":\"cust-123\",\"amount\":12990,\"currency\":\"BRL\",\"method\":\"card\"}"))
        payment-id (:id (response-body created))
        response (handler (request :get (str "/v1/payments/" payment-id) ""))]
    (is (= 200 (:status response)))
    (is (= payment-id (:id (response-body response))))))

(deftest get-missing-payment-returns-not-found
  (let [response ((api) (request :get "/v1/payments/fc1b6fa6-1ed5-4211-a9fd-fb4e1dfefa0d" ""))]
    (is (= 404 (:status response)))
    (is (= "payment_not_found" (get-in (response-body response) [:error :code])))))

(deftest invalid-as-of-is-rejected-without-changing-the-current-contract
  (let [response ((api) {:request-method :get
                          :uri "/v1/payments/fc1b6fa6-1ed5-4211-a9fd-fb4e1dfefa0d"
                          :query-params {"asOf" "not-a-temporal-point"}
                          :headers {"x-api-key" "test-api-key"} :body (ByteArrayInputStream. (byte-array 0))})]
    (is (= 400 (:status response)))
    (is (= "invalid_as_of" (get-in (response-body response) [:error :code])))))

(deftest invalid-payment-request-returns-bad-request
  (let [response ((api) (request :post "/v1/payments"
                                  "{\"customerId\":\"cust-123\",\"amount\":12990,\"currency\":\"BRL\"}"))]
    (is (= 400 (:status response)))
    (is (= "invalid_payment" (get-in (response-body response) [:error :code])))))

(deftest negative-amount-returns-bad-request
  (let [response ((api) (request :post "/v1/payments"
                                  "{\"customerId\":\"cust-123\",\"amount\":-1,\"currency\":\"BRL\",\"method\":\"card\"}"))]
    (is (= 400 (:status response)))
    (is (= "invalid_payment" (get-in (response-body response) [:error :code])))))

(deftest raw-card-data-is-rejected-by-the-closed-request-schema
  (let [response ((api) (request :post "/v1/payments"
                                  "{\"customerId\":\"cust-123\",\"amount\":12990,\"currency\":\"BRL\",\"method\":\"card\",\"cardNumber\":\"4242424242424242\"}"))]
    (is (= 400 (:status response)))
    (is (= "invalid_payment" (get-in (response-body response) [:error :code])))))

(deftest malformed-json-returns-bad-request
  (let [response ((api) (request :post "/v1/payments" "{not-json"))]
    (is (= 400 (:status response)))
    (is (= "invalid_json" (get-in (response-body response) [:error :code])))))

(deftest missing-idempotency-key-returns-bad-request
  (let [response ((api) {:request-method :post
                          :uri "/v1/payments"
                          :headers {"content-type" "application/json" "x-api-key" "test-api-key"}
                          :body (ByteArrayInputStream. (.getBytes "{\"customerId\":\"cust-123\",\"amount\":12990,\"currency\":\"BRL\",\"method\":\"card\"}" "UTF-8"))})]
    (is (= 400 (:status response)))
    (is (= "missing_idempotency_key" (get-in (response-body response) [:error :code])))))

(deftest same-idempotency-key-and-payload-replays-the-original-payment
  (let [handler (api)
        body "{\"customerId\":\"cust-123\",\"amount\":12990,\"currency\":\"BRL\",\"method\":\"card\"}"
        first-response (handler (request :post "/v1/payments" body "same-key"))
        replay-response (handler (request :post "/v1/payments" body "same-key"))]
    (is (= 201 (:status first-response)))
    (is (= 200 (:status replay-response)))
    (is (= (:id (response-body first-response)) (:id (response-body replay-response))))))

(deftest same-idempotency-key-with-a-different-payload-conflicts
  (let [handler (api)
        first-body "{\"customerId\":\"cust-123\",\"amount\":1000,\"currency\":\"BRL\",\"method\":\"card\"}"
        second-body "{\"customerId\":\"cust-123\",\"amount\":2000,\"currency\":\"BRL\",\"method\":\"card\"}"
        _ (handler (request :post "/v1/payments" first-body "conflict-key"))
        response (handler (request :post "/v1/payments" second-body "conflict-key"))]
    (is (= 409 (:status response)))
    (is (= "idempotency_conflict" (get-in (response-body response) [:error :code])))))
