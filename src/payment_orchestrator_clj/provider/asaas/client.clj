(ns payment-orchestrator-clj.provider.asaas.client
  "Small Asaas HTTP boundary. It never logs the access token or response body."
  (:require [clojure.data.json :as json]
            [payment-orchestrator-clj.provider.asaas.errors :as errors])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.time Duration]))

(defprotocol AsaasClient (request! [client request]))

(defrecord LiveAsaasClient [api-key base-url timeout-ms http-client]
  AsaasClient
  (request! [_ {:keys [method path body]}]
    (try
      (let [builder (doto (HttpRequest/newBuilder (URI/create (str base-url path)))
                      (.timeout (Duration/ofMillis timeout-ms))
                      (.header "access_token" api-key) (.header "Content-Type" "application/json")
                      (.header "User-Agent" "payment-orchestrator-clj"))
            request-builder (case method
                              :post (.POST builder (HttpRequest$BodyPublishers/ofString (json/write-str body)))
                              :get (.GET builder)
                              :delete (.DELETE builder)
                              (throw (ex-info "Unsupported Asaas HTTP method" {:method method})))
            response (.send http-client (.build request-builder) (HttpResponse$BodyHandlers/ofString))
            normalized {:status (.statusCode response) :body (json/read-str (.body response) :key-fn keyword)
                        :request-id (first (.allValues (.headers response) "request-id"))}]
        (if (<= 200 (:status normalized) 299) normalized
            (throw (errors/response-error (assoc normalized :endpoint path)))))
      (catch java.net.http.HttpTimeoutException _ (throw (errors/timeout-error)))
      (catch java.io.IOException _ (throw (errors/timeout-error))))))

(defrecord StubAsaasClient [handler] AsaasClient (request! [_ request] (handler request)))
(defn new-client [{:keys [api-key base-url timeout-ms request-handler] :or {base-url "https://api-sandbox.asaas.com/v3" timeout-ms 10000}}]
  (if request-handler (->StubAsaasClient request-handler) (->LiveAsaasClient api-key base-url timeout-ms (HttpClient/newHttpClient))))
