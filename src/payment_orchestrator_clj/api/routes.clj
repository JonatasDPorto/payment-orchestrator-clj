(ns payment-orchestrator-clj.api.routes
  (:require [reitit.ring :as ring]
            [payment-orchestrator-clj.api.payment :as payment]))

(defn handler [dependencies]
  (ring/ring-handler
   (ring/router
    [["/v1/payments" {:post {:handler (payment/create-payment-handler dependencies)}}]
     ["/v1/payments/:id" {:get {:handler (payment/find-payment-handler dependencies)}}]])
   (ring/create-default-handler)))
