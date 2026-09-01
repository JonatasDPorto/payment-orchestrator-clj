(ns payment-orchestrator-clj.observability.trace
  "Tracing boundary. Runtime exporters are injected; the application works with a no-op exporter when no collector is configured."
  (:require [clojure.string :as string])
  (:import [java.util UUID]))

(defn- identifier [] (str (UUID/randomUUID)))
(defn new-tracer ([] (new-tracer {})) ([{:keys [export!]}] {:export! export!}))
(defn test-tracer [spans] (new-tracer {:export! #(swap! spans conj %)}))

(defn root-context [request-id correlation-id traceparent]
  {:trace-id (or (some-> traceparent (string/split #"-") second) (identifier))
   :span-id nil :request-id request-id :correlation-id correlation-id})
(defn enrich-context [context fields] (merge context (select-keys fields [:payment-id :merchant-id :provider :operation])))
(defn context-fields [context] (select-keys context [:trace-id :span-id :request-id :correlation-id :payment-id :merchant-id :provider :operation]))

(defn with-span
  "Executes f inside a child span. Context is passed explicitly; no request state is global."
  [tracer context name attributes f]
  (let [started (System/nanoTime)
        span-context (assoc context :parent-span-id (:span-id context) :span-id (identifier) :operation name)]
    (try
      (let [result (f span-context)
            span (merge {:name name :status :success :duration-ms (/ (- (System/nanoTime) started) 1000000.0)}
                        (context-fields span-context) attributes)]
        (when-let [export! (:export! tracer)] (export! span)) result)
      (catch Exception error
        (let [data (ex-data error)
              span (merge {:name name :status :error :error-category (or (:provider/error data) (:error/code data) :error/unknown)
                           :duration-ms (/ (- (System/nanoTime) started) 1000000.0)}
                          (context-fields span-context) attributes)]
          (when-let [export! (:export! tracer)] (export! span))
          (throw error))))))
