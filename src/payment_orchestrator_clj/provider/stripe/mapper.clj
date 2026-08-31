(ns payment-orchestrator-clj.provider.stripe.mapper
  "Pure translations between the canonical gateway contract and Stripe payloads."
  (:require [clojure.string :as string]
            [payment-orchestrator-clj.provider.port :as port]))

(defn operation-idempotency-key [operation-id operation]
  (str "payment-orchestrator-clj:" operation ":" operation-id))

(defn create-request [command payment-method-id]
  {:method :post
   :path "/v1/payment_intents"
   :idempotency-key (operation-idempotency-key (:operation/id command) "create-payment")
   :form {"amount" (str (:amount command))
          "currency" (string/lower-case (name (:currency command)))
          "confirm" "true"
          "payment_method" payment-method-id
          "payment_method_types[]" "card"
          "metadata[payment_id]" (str (:payment/id command))}})

(defn fetch-request [reference]
  {:method :get :path (str "/v1/payment_intents/" reference)})

(defn cancel-request [command]
  {:method :post
   :path (str "/v1/payment_intents/" (:provider-payment/reference command) "/cancel")
   :idempotency-key (operation-idempotency-key (:operation/id command) "cancel-payment")
   :form {}})

(defn refund-request [command]
  {:method :post
   :path "/v1/refunds"
   :idempotency-key (operation-idempotency-key (:operation/id command) "refund-payment")
   :form {"payment_intent" (:provider-payment/reference command)}})

(defn- canonical-status [stripe-status]
  (case stripe-status
    "succeeded" :provider.status/succeeded
    "processing" :provider.status/processing
    "requires_action" :provider.status/requires-action
    "requires_confirmation" :provider.status/processing
    "requires_capture" :provider.status/processing
    "requires_payment_method" :provider.status/failed
    "canceled" :provider.status/cancelled
    (throw (port/provider-error :provider.error/unexpected-response
                                {:provider :stripe :retryable? false :outcome-known? true}))))

(defn payment-intent->provider-result [{:keys [body request-id]}]
  (let [status (:status body)]
    (cond-> {:provider :stripe
             :provider-payment/reference (:id body)
             :provider-payment/status (canonical-status status)
             :provider-payment/raw-status status
             :provider-request-id request-id}
      (= "requires_action" status)
      (assoc :provider-payment/action
             {:action/type :client-secret
              :action/value (:client_secret body)}))))

(defn refund->provider-result [{:keys [body request-id]}]
  {:provider :stripe
   :provider-payment/reference (:payment_intent body)
   :provider-payment/status (if (= "succeeded" (:status body))
                              :provider.status/succeeded
                              :provider.status/processing)
   :provider-payment/raw-status (:status body)
   :provider-request-id request-id})
