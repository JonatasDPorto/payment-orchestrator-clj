(ns payment-orchestrator-clj.provider.stripe.client
  "Small Stripe HTTP boundary. It never logs authorization or response payloads."
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [payment-orchestrator-clj.provider.stripe.errors :as errors])
  (:import [java.net URI URLEncoder]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.nio.charset StandardCharsets]
           [java.time Duration]
           [java.util Base64]
           [java.util.concurrent TimeoutException]))

(defprotocol StripeClient
  (request! [client request]))

(defn- form-body [form]
  (->> form
       (map (fn [[key value]]
              (str (URLEncoder/encode (str key) StandardCharsets/UTF_8)
                   "="
                   (URLEncoder/encode (str value) StandardCharsets/UTF_8))))
       (string/join "&")))

(defn- authorization [secret-key]
  (str "Basic " (.encodeToString (Base64/getEncoder)
                                  (.getBytes (str secret-key ":") StandardCharsets/UTF_8))))

(defrecord LiveStripeClient [secret-key timeout-ms http-client]
  StripeClient
  (request! [_ {:keys [method path form idempotency-key]}]
    (try
      (let [builder (doto (HttpRequest/newBuilder (URI/create (str "https://api.stripe.com" path)))
                      (.timeout (Duration/ofMillis timeout-ms))
                      (.header "Authorization" (authorization secret-key))
                      (.header "Accept" "application/json"))
            builder (if (= :post method)
                      (doto builder
                        (.header "Content-Type" "application/x-www-form-urlencoded")
                        (.POST (HttpRequest$BodyPublishers/ofString (form-body form))))
                      (.GET builder))
            builder (if idempotency-key
                      (.header builder "Idempotency-Key" idempotency-key)
                      builder)
            response (.send http-client (.build builder) (HttpResponse$BodyHandlers/ofString))
            body (json/read-str (.body response) :key-fn keyword)
            normalized {:status (.statusCode response)
                        :body body
                        :request-id (first (.allValues (.headers response) "Request-Id"))}]
        (if (<= 200 (:status normalized) 299)
          normalized
          (throw (errors/response-error normalized))))
      (catch TimeoutException _ (throw (errors/timeout-error)))
      (catch java.net.http.HttpTimeoutException _ (throw (errors/timeout-error)))
      (catch java.io.IOException _ (throw (errors/transport-error))))))

(defrecord StubStripeClient [handler]
  StripeClient
  (request! [_ request] (handler request)))

(defn new-client [{:keys [secret-key timeout-ms request-handler]
                   :or {timeout-ms 10000}}]
  (if request-handler
    (->StubStripeClient request-handler)
    (->LiveStripeClient secret-key timeout-ms (HttpClient/newHttpClient))))
