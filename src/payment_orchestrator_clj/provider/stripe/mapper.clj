(ns payment-orchestrator-clj.provider.stripe.mapper
  "Pure translations between the canonical gateway contract and Stripe payloads."
  (:require [clojure.string :as string]
            [payment-orchestrator-clj.provider.port :as port]))

(defn operation-idempotency-key [operation-id operation]
  (str "payment-orchestrator-clj:" operation ":" operation-id))

(defn create-request [command payment-method-id]
  (let [common {"amount" (str (:amount command))
                "currency" (string/lower-case (name (:currency command)))
                "metadata[payment_id]" (str (:payment/id command))}
        form (case (or (:method command) :payment.method/card)
               (:payment.method/card :card) (do
                                               (when-not (string? payment-method-id)
                                                 (throw (port/provider-error :provider.error/invalid-request
                                                                             {:provider :stripe :retryable? false :outcome-known? true})))
                                               (assoc common "confirm" "true"
                                                             "payment_method" payment-method-id
                                                             "payment_method_types[]" "card"))
               (:payment.method/pix :pix) (let [{:keys [tax-id email name]} (:pix command)]
                                             (when-not (and (string? tax-id) (string? email) (string? name))
                                               (throw (port/provider-error :provider.error/invalid-request
                                                                           {:provider :stripe :retryable? false :outcome-known? true})))
                                             (assoc common
                                                    "confirm" "true"
                                                    "payment_method_types[]" "pix"
                                                    "payment_method_data[type]" "pix"
                                                    "payment_method_data[billing_details][name]" name
                                                    "payment_method_data[billing_details][email]" email
                                                    "payment_method_data[billing_details][tax_id]" tax-id))
               (:payment.method/boleto :boleto) (let [{:keys [tax-id email name address]} (:boleto command)
                                                      {:keys [line1 city state postal-code country]} address]
                                                  (when-not (and (string? tax-id) (string? email) (string? name)
                                                                 (string? line1) (string? city) (string? state)
                                                                 (string? postal-code) (= "BR" country))
                                                    (throw (port/provider-error :provider.error/invalid-request
                                                                                {:provider :stripe :retryable? false :outcome-known? true})))
                                                  (assoc common
                                                         "confirm" "true"
                                                         "payment_method_types[]" "boleto"
                                                         "payment_method_data[type]" "boleto"
                                                         "payment_method_data[boleto][tax_id]" tax-id
                                                         "payment_method_data[billing_details][name]" name
                                                         "payment_method_data[billing_details][email]" email
                                                         "payment_method_data[billing_details][address][line1]" line1
                                                         "payment_method_data[billing_details][address][city]" city
                                                         "payment_method_data[billing_details][address][state]" state
                                                         "payment_method_data[billing_details][address][postal_code]" postal-code
                                                         "payment_method_data[billing_details][address][country]" country))
               (throw (port/provider-error :provider.error/invalid-request
                                           {:provider :stripe :retryable? false :outcome-known? true})))]
    {:method :post
     :path "/v1/payment_intents"
     :idempotency-key (operation-idempotency-key (:operation/id command) "create-payment")
     :form form}))

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
   :form {"payment_intent" (:provider-payment/reference command)
          "amount" (str (:refund/amount command))}})

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

(defn- pix-action [body]
  (let [qr-code (get-in body [:next_action :pix_display_qr_code])]
    (when-not (and (string? (:data qr-code)) (number? (:expires_at qr-code)))
      (throw (port/provider-error :provider.error/unexpected-response
                                  {:provider :stripe :retryable? false :outcome-known? true})))
    (cond-> {:action/type :pix/qr-code
             :action/payload (:data qr-code)
             :action/expires-at (java.time.Instant/ofEpochSecond (:expires_at qr-code))}
      (or (:image_url_svg qr-code) (:image_url_png qr-code))
      (assoc :action/qr-code-url (or (:image_url_svg qr-code) (:image_url_png qr-code)))
      (:hosted_instructions_url qr-code)
      (assoc :action/hosted-instructions-url (:hosted_instructions_url qr-code)))))

(defn- boleto-action [body]
  (let [details (get-in body [:next_action :boleto_display_details])]
    (when-not (and (string? (:number details))
                   (string? (:hosted_voucher_url details))
                   (number? (:expires_at details)))
      (throw (port/provider-error :provider.error/unexpected-response
                                  {:provider :stripe :retryable? false :outcome-known? true})))
    (cond-> {:action/type :boleto/voucher
             :action/payload (:number details)
             :action/hosted-instructions-url (:hosted_voucher_url details)
             :action/expires-at (java.time.Instant/ofEpochSecond (:expires_at details))}
      (:pdf details) (assoc :action/document-url (:pdf details)))))

(defn payment-intent->provider-result
  ([response] (payment-intent->provider-result response nil))
  ([{:keys [body request-id]} method]
   (let [status (:status body)]
    (cond-> {:provider :stripe
             :provider-payment/reference (:id body)
             :provider-payment/status (canonical-status status)
             :provider-payment/raw-status status
             :provider-request-id request-id}
      (= "requires_action" status)
      (assoc :provider-payment/action
             (if (= "pix_display_qr_code" (get-in body [:next_action :type]))
               (pix-action body)
               (if (= "boleto_display_details" (get-in body [:next_action :type]))
                 (boleto-action body)
                 {:action/type :client-secret
                  :action/value (:client_secret body)})))
      ))))

(defn refund->provider-result [{:keys [body request-id]}]
  {:provider :stripe
   :provider-refund/reference (:id body)
   :provider-payment/reference (:payment_intent body)
   :provider-payment/status (if (= "succeeded" (:status body))
                              :provider.status/succeeded
                              :provider.status/processing)
   :provider-payment/raw-status (:status body)
   :provider-request-id request-id})
