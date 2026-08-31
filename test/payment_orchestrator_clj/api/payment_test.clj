(ns payment-orchestrator-clj.api.payment-test
  (:require [clojure.data.json :as json]
            [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.api.routes :as routes]
            [payment-orchestrator-clj.provider.fake :as fake]
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

(defn- api []
  (routes/handler {:payments (->InMemoryPayments (atom {}) (atom {}))
                   :gateway (fake/new-gateway {:mode :always-success})
                   :clock #(Instant/parse "2026-08-30T12:00:00Z")
                   :id-generator #(UUID/fromString "fc1b6fa6-1ed5-4211-a9fd-fb4e1dfefa0d")}))

(defn- request [method uri body & [key]]
  {:request-method method
   :uri uri
   :headers (cond-> {"content-type" "application/json"}
              (= method :post) (assoc "idempotency-key" (or key "test-key")))
   :body (ByteArrayInputStream. (.getBytes body "UTF-8"))})

(defn- response-body [response]
  (json/read-str (:body response) :key-fn keyword))

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

(deftest malformed-json-returns-bad-request
  (let [response ((api) (request :post "/v1/payments" "{not-json"))]
    (is (= 400 (:status response)))
    (is (= "invalid_json" (get-in (response-body response) [:error :code])))))

(deftest missing-idempotency-key-returns-bad-request
  (let [response ((api) {:request-method :post
                          :uri "/v1/payments"
                          :headers {"content-type" "application/json"}
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
