(ns payment-orchestrator-clj.provider.asaas.adapter
  "Asaas implementation of the provider port; Asaas concepts stay in this module."
  (:require [payment-orchestrator-clj.provider.asaas.client :as client]
            [payment-orchestrator-clj.provider.asaas.mapper :as mapper]
            [payment-orchestrator-clj.provider.port :as port])
  (:import [java.time LocalDate]))

(defn- required-env [environment name]
  (or (get environment name)
      (System/getenv name)
      (throw (port/provider-error :provider.error/authentication
                                  {:provider :asaas :retryable? false :outcome-known? true}))))

(defn- configured-base-url [{:keys [base-url base-url-env environment sandbox? sandbox-base-url]}]
  (or base-url
      (when base-url-env
        (or (get environment base-url-env) (System/getenv base-url-env)))
      (when sandbox? sandbox-base-url)
      "https://api-sandbox.asaas.com/v3"))

(defrecord AsaasGateway [client due-date customer-id]
  port/PaymentGateway
  (capabilities [_] #{:payment/create :payment/fetch :payment/refund :payment/cancel
                      :method/card :method/pix :method/boleto})
  (create-payment! [_ command]
    (let [payment-response (client/request! client
                                            (mapper/create-request
                                             (update command :customer #(or % {:reference customer-id}))
                                             due-date))]
      (if (= :payment.method/pix (:method command))
        (mapper/payment-with-pix-action->provider-result
         payment-response
         (client/request! client (mapper/pix-qr-code-request (get-in payment-response [:body :id]))))
        (mapper/payment->provider-result payment-response))))
  (fetch-payment [_ reference]
    (let [payment-response (client/request! client (mapper/fetch-request reference))]
      (if (= "PIX" (get-in payment-response [:body :billingType]))
        (mapper/payment-with-pix-action->provider-result
         payment-response (client/request! client (mapper/pix-qr-code-request reference)))
        (mapper/payment->provider-result payment-response))))
  (cancel-payment! [_ command]
    (mapper/deleted-payment->provider-result
     (client/request! client (mapper/cancel-request command))))
  (refund-payment! [_ command]
    (mapper/refund->provider-result
     (client/request! client (mapper/refund-request command)))))

(defn new-gateway
  "Creates an Asaas gateway. Secrets are read only when no test client is injected.
  `due-date` is ISO-8601 because Asaas requires one for every charge."
  [{:keys [api-key api-key-env timeout-ms environment request-handler due-date customer-id]
    :or {api-key-env "ASAAS_API_KEY" timeout-ms 10000 environment {}}
    :as options}]
  (let [injected? (some? request-handler)
        api-key (when-not injected? (or api-key (required-env environment api-key-env)))
        base-url (configured-base-url options)]
    (->AsaasGateway (client/new-client {:api-key api-key
                                         :base-url base-url
                                         :timeout-ms timeout-ms
                                         :request-handler request-handler})
                    (or due-date (str (LocalDate/now)))
                    customer-id)))
