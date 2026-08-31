(ns payment-orchestrator-clj.api.payment
  "HTTP DTO validation and thin payment handlers."
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [malli.core :as m]
            [malli.error :as me]
            [payment-orchestrator-clj.payment.repository :as repository]
            [payment-orchestrator-clj.ledger.repository :as ledger]
            [payment-orchestrator-clj.payment.service :as service])
  (:import [java.util UUID]))

(def create-payment-request
  [:map {:closed true}
   [:customerId [:and string? [:fn (complement clojure.string/blank?)]]]
   [:amount [:and int? [:fn pos?]]]
   [:currency [:enum "BRL"]]
   [:method [:enum "card"]]])

(defn- json-response [status body]
  {:status status
   :headers {"content-type" "application/json; charset=utf-8"}
   :body (json/write-str body)})

(defn- error-response [status code message details]
  (json-response status {:error {:code code :message message :details details}}))

(defn- decode-body [request]
  (json/read-str (slurp (:body request)) :key-fn keyword))

(defn- request->command [request]
  {:customer-id (:customerId request)
   :amount (:amount request)
   :currency (keyword (:currency request))
   :method (keyword "payment.method" (:method request))})

(defn payment-response [payment]
  {:id (str (:payment/id payment))
   :status (name (:payment/status payment))
   :amount (:payment/amount payment)
   :currency (name (:payment/currency payment))})

(defn- request-context [request]
  (let [request-id (or (get-in request [:headers "x-request-id"])
                       (str (UUID/randomUUID)))]
    {:request-id request-id :correlation-id request-id :source :source/http}))

(defn- idempotency-key [request]
  (let [key (get-in request [:headers "idempotency-key"])]
    (when-not (string/blank? key) key)))

(defn create-payment-handler [dependencies]
  (fn [request]
    (let [body (try
                 (decode-body request)
                 (catch Exception _ ::invalid-json))]
      (if (= ::invalid-json body)
        (error-response 400 "invalid_json" "Request body must be valid JSON" {})
        (try
          (if-not (m/validate create-payment-request body)
            (error-response 400 "invalid_payment" "Payment request is invalid"
                            {:validation (me/humanize (m/explain create-payment-request body))})
          (if-let [key (idempotency-key request)]
            (let [{:keys [outcome payment]} (service/create-payment-idempotently!
                                              dependencies (request->command body) key (request-context request))]
              (case outcome
                :conflict (error-response 409 "idempotency_conflict"
                                          "Idempotency-Key was already used with a different request" {})
                (assoc (json-response (if (= :created outcome) 201 200) (payment-response payment))
                       :headers {"content-type" "application/json; charset=utf-8"
                                 "location" (str "/v1/payments/" (:payment/id payment))})))
            (error-response 400 "missing_idempotency_key" "Idempotency-Key header is required" {})))
          (catch clojure.lang.ExceptionInfo error
            (if (:provider/error? (ex-data error))
              (error-response 502 "provider_error" "Payment provider could not complete the operation" {})
              (error-response 400 "invalid_payment" "Payment request is invalid" (ex-data error)))))))))

(defn find-payment-handler [dependencies]
  (fn [request]
    (let [payment-id (try
                       (UUID/fromString (get-in request [:path-params :id]))
                       (catch IllegalArgumentException _ nil))
          payment (when payment-id (repository/find-payment (:payments dependencies) payment-id))]
      (if payment
        (json-response 200 (payment-response payment))
        (error-response 404 "payment_not_found" "Payment was not found" {})))))

(defn payment-ledger-handler [dependencies]
  (fn [request]
    (let [payment-id (try (UUID/fromString (get-in request [:path-params :id]))
                          (catch IllegalArgumentException _ nil))
          payment (when payment-id (repository/find-payment (:payments dependencies) payment-id))]
      (if payment
        (json-response 200 {:paymentId (str payment-id)
                            :journals (mapv (fn [journal]
                                              {:id (str (:journal/id journal))
                                               :type (name (:journal/type journal))
                                               :postings (mapv (fn [posting]
                                                                 {:id (str (:posting/id posting))
                                                                  :account (get-in posting [:posting/account :ledger-account/code])
                                                                  :side (name (:posting/side posting))
                                                                  :amount (:posting/amount posting)
                                                                  :currency (name (:posting/currency posting))})
                                                               (:journal/postings journal))})
                                            (ledger/payment-journals (:ledger dependencies) payment-id))})
        (error-response 404 "payment_not_found" "Payment was not found" {})))))
