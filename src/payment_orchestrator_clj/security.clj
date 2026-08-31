(ns payment-orchestrator-clj.security
  (:require [clojure.string :as string])
  (:import [java.io ByteArrayInputStream InputStream]
           [java.nio.charset StandardCharsets]
           [java.security MessageDigest]))

(defn secure= [left right]
  (and (string? left) (string? right)
       (MessageDigest/isEqual (.getBytes left StandardCharsets/UTF_8) (.getBytes right StandardCharsets/UTF_8))))

(defn required-api-key [value]
  (if (string/blank? value)
    (throw (ex-info "Payment Orchestrator API key is not configured"
                    {:configuration-error :api-key-missing}))
    value))
(defn- bearer-token [request]
  (some-> (get-in request [:headers "authorization"])
          (string/replace-first #"(?i)^Bearer\s+" "")))

(defn- supplied-api-key [request]
  (or (bearer-token request)
      (get-in request [:headers "x-api-key"])))

(defn- json-response [status code]
  {:status status
   :headers {"content-type" "application/json; charset=utf-8"}
   :body (str "{\"error\":{\"code\":\"" code "\"}}")})

(defn new-rate-limiter [{:keys [limit window-ms]}]
  {:limit limit :window-ms window-ms :buckets (atom {})})

(defn allow! [rate-limiter key now-ms]
  (let [{:keys [limit window-ms buckets]} rate-limiter
        window-start (* window-ms (quot now-ms window-ms))]
    (loop []
      (let [before @buckets
            bucket (get before key {:window-start window-start :count 0})
            current (if (= window-start (:window-start bucket))
                      bucket
                      {:window-start window-start :count 0})]
        (if (>= (:count current) limit)
          false
          (if (compare-and-set! buckets before (assoc before key (update current :count inc)))
            true
            (recur)))))))

(defn wrap-rate-limit [handler rate-limiter clock]
  (if-not rate-limiter
    handler
    (fn [request]
      (let [client (or (:remote-addr request) "unknown")
            key [(:request-method request) (:uri request) client]]
        (if (allow! rate-limiter key (clock))
          (handler request)
          (json-response 429 "rate_limited"))))))

(defn wrap-body-limit [handler max-bytes]
  (fn [request]
    (let [content-length (some-> (get-in request [:headers "content-length"])
                                  parse-long)
          body ^InputStream (:body request)]
      (cond
        (and content-length (> content-length max-bytes))
        (json-response 413 "payload_too_large")
        body (let [bytes (.readNBytes body (inc max-bytes))]
               (if (> (alength bytes) max-bytes)
                 (json-response 413 "payload_too_large")
                 (handler (assoc request :body (ByteArrayInputStream. bytes)))))
        :else (handler request)))))

(defn wrap-api-key [handler api-key]
  (fn [request]
    (if (or (string/starts-with? (:uri request) "/webhooks/")
            (secure= api-key (supplied-api-key request)))
      (handler request)
      (json-response 401 "unauthorized"))))
