(ns payment-orchestrator-clj.observability.http
  (:require [payment-orchestrator-clj.observability.log :as log]
            [payment-orchestrator-clj.observability.metrics :as metrics]
            [payment-orchestrator-clj.observability.trace :as trace])
  (:import [java.util UUID]))

(defn wrap-observability [handler registry tracer]
  (let [registry (or registry (metrics/registry)) tracer (or tracer (trace/new-tracer))]
    (fn [request]
      (let [started (System/nanoTime) request-id (or (get-in request [:headers "x-request-id"]) (str (UUID/randomUUID)))
          correlation-id (or (get-in request [:headers "x-correlation-id"]) request-id)
          context (trace/root-context request-id correlation-id (get-in request [:headers "traceparent"]))
          response (trace/with-span tracer context "http.request" {:http-method (name (:request-method request)) :http-route (:uri request)}
                                    #(handler (assoc request :observability/context % :observability/request-id request-id :observability/correlation-id correlation-id)))
          duration (/ (- (System/nanoTime) started) 1000000.0)]
        (metrics/inc! registry "http_request_total")
        (metrics/observe! registry "http_request_duration_seconds" (/ duration 1000.0))
        (log/event (merge (trace/context-fields context)
                          {:request_id request-id :correlation_id correlation-id
                           :operation (str (:request-method request) " " (:uri request))
                           :duration_ms duration :status (:status response)}))
        (assoc-in response [:headers "x-request-id"] request-id)))))
