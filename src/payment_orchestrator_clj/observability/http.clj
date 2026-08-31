(ns payment-orchestrator-clj.observability.http
  (:require [payment-orchestrator-clj.observability.log :as log]
            [payment-orchestrator-clj.observability.metrics :as metrics]
            [payment-orchestrator-clj.observability.trace :as trace])
  (:import [java.util UUID]))

(defn wrap-observability [handler registry]
  (let [registry (or registry (metrics/registry))]
    (fn [request]
      (let [started (System/nanoTime) request-id (or (get-in request [:headers "x-request-id"]) (str (UUID/randomUUID)))
          response (trace/with-span "http.request" #(handler (assoc request :observability/request-id request-id
                                                                     :observability/correlation-id request-id)))
          duration (/ (- (System/nanoTime) started) 1000000.0)]
        (metrics/inc! registry "http_request_total")
        (log/event {:request_id request-id :operation (str (:request-method request) " " (:uri request))
                    :duration_ms duration :status (:status response)})
        (assoc-in response [:headers "x-request-id"] request-id)))))
