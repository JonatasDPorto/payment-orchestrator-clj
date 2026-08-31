(ns payment-orchestrator-clj.provider.stripe.adapter
  "Stripe implementation of the provider port; Stripe concepts stay in this module."
  (:require [payment-orchestrator-clj.provider.port :as port]
            [payment-orchestrator-clj.provider.stripe.client :as client]
            [payment-orchestrator-clj.provider.stripe.mapper :as mapper]))

(defn- required-env [environment name]
  (or (get environment name)
      (System/getenv name)
      (throw (port/provider-error :provider.error/authentication
                                  {:provider :stripe :retryable? false :outcome-known? true}))))

(defrecord StripeGateway [client payment-method-id]
  port/PaymentGateway
  (capabilities [_] #{:payment/create :payment/fetch :payment/refund :payment/cancel :method/card})
  (create-payment! [_ command]
    (mapper/payment-intent->provider-result
     (client/request! client (mapper/create-request command payment-method-id))))
  (fetch-payment [_ reference]
    (mapper/payment-intent->provider-result
     (client/request! client (mapper/fetch-request reference))))
  (cancel-payment! [_ command]
    (mapper/payment-intent->provider-result
     (client/request! client (mapper/cancel-request command))))
  (refund-payment! [_ command]
    (mapper/refund->provider-result
     (client/request! client (mapper/refund-request command)))))

(defn new-gateway
  "Creates a Stripe gateway. Secrets are read only when no test client is injected."
  [{:keys [secret-key-env payment-method-env timeout-ms environment request-handler]
    :or {secret-key-env "STRIPE_SECRET_KEY"
         payment-method-env "STRIPE_TEST_PAYMENT_METHOD"
         timeout-ms 10000
         environment {}}}]
  (let [injected? (some? request-handler)
        secret-key (when-not injected? (required-env environment secret-key-env))
        payment-method-id (if injected?
                            "pm_test_stub"
                            (required-env environment payment-method-env))]
    (->StripeGateway (client/new-client {:secret-key secret-key
                                         :timeout-ms timeout-ms
                                         :request-handler request-handler})
                     payment-method-id)))
