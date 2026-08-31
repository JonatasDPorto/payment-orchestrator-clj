(ns payment-orchestrator-clj.api.routes
  (:require [reitit.ring :as ring]
            [payment-orchestrator-clj.api.payment :as payment]
            [payment-orchestrator-clj.observability.http :as observability]
            [payment-orchestrator-clj.observability.metrics :as metrics]
            [payment-orchestrator-clj.security :as security]
            [payment-orchestrator-clj.api.webhook :as webhook]))

(defn handler [dependencies]
  (let [router (ring/ring-handler
   (ring/router
     [["/metrics" {:get {:handler (fn [_] {:status 200 :headers {"content-type" "text/plain"} :body (metrics/render (:metrics dependencies))})}}]
      ["/v1/payments" {:post {:handler (payment/create-payment-handler dependencies)}}]
     ["/v1/payments/:id/history" {:get {:handler (payment/payment-history-handler dependencies)}}]
     ["/v1/payments/:id/ledger" {:get {:handler (payment/payment-ledger-handler dependencies)}}]
     ["/v1/payments/:id" {:get {:handler (payment/find-payment-handler dependencies)}}]
     ["/webhooks/stripe" {:post {:handler (webhook/stripe-handler dependencies)}}]])
   (ring/create-default-handler))]
    (-> router
        (security/wrap-api-key (:api-key dependencies))
        (security/wrap-rate-limit (:rate-limiter dependencies) #(System/currentTimeMillis))
        (security/wrap-body-limit (or (:max-request-body-bytes dependencies) 1048576))
        (observability/wrap-observability (:metrics dependencies)))))
