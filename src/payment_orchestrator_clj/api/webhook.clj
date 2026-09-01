(ns payment-orchestrator-clj.api.webhook
  (:require [payment-orchestrator-clj.provider.stripe.webhook :as stripe]
            [payment-orchestrator-clj.provider.asaas.webhook :as asaas]
            [payment-orchestrator-clj.webhook.service :as service])
  (:import [java.time Instant]))

(defn- response [status body]
  {:status status :headers {"content-type" "application/json; charset=utf-8"} :body body})

(defn stripe-handler [{:keys [stripe-webhook-secret clock dispatcher] :as dependencies}]
  (fn [request]
    (let [raw-body (slurp (:body request))
          signature (get-in request [:headers "stripe-signature"])]
      (if (and stripe-webhook-secret
               (stripe/valid-signature? raw-body signature stripe-webhook-secret clock))
        (try
          (let [result (service/enqueue-stripe-event! dependencies raw-body)]
            (when (= :accepted (:outcome result))
              (dispatcher dependencies))
            (response 200 "{\"received\":true}"))
          (catch Exception _ (response 400 "{\"error\":\"invalid_event\"}")))
        (response 400 "{\"error\":\"invalid_signature\"}")))))

(defn asaas-handler [{:keys [asaas-webhook-token dispatcher] :as dependencies}]
  (fn [request]
    (let [raw-body (slurp (:body request)) token (get-in request [:headers "asaas-access-token"])]
      (if (and asaas-webhook-token (asaas/valid-token? token asaas-webhook-token))
        (try
          (let [result (service/enqueue-asaas-event! dependencies raw-body)]
            (when (= :accepted (:outcome result)) (dispatcher dependencies))
            (response 200 "{\"received\":true}"))
          ;; Invalid identity is rejected before the inbox; no account or token
          ;; detail is reflected to the caller.
          (catch clojure.lang.ExceptionInfo error
            (if (contains? #{:webhook/invalid-event :webhook/unknown-provider-account
                             :webhook/inactive-provider-account}
                           (:error/code (ex-data error)))
              (response 400 "{\"error\":\"invalid_event\"}")
              (throw error))))
        (response 400 "{\"error\":\"invalid_token\"}")))))
