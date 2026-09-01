(ns payment-orchestrator-clj.provider.asaas.mapper
  "Pure translations between the canonical gateway contract and Asaas payloads."
  (:require [clojure.string :as string]
            [payment-orchestrator-clj.provider.port :as port])
  (:import [java.time Instant LocalDateTime ZoneId]))

(defn operation-idempotency-key [operation-id operation]
  (str "payment-orchestrator-clj:" operation ":" operation-id))

(defn- invalid-request! []
  (throw (port/provider-error :provider.error/invalid-request
                              {:provider :asaas :retryable? false :outcome-known? true})))

(defn- unexpected-response! []
  (throw (port/provider-error :provider.error/unexpected-response
                              {:provider :asaas :retryable? false :outcome-known? true})))

(defn- billing-type [method]
  (case (or method :payment.method/card)
    (:payment.method/card :card) "CREDIT_CARD"
    (:payment.method/pix :pix) "PIX"
    (:payment.method/boleto :boleto) "BOLETO"
    (invalid-request!)))

(defn- amount->reais [amount]
  (when-not (and (integer? amount) (pos? amount))
    (invalid-request!))
  (.movePointLeft (bigdec amount) 2))

(defn create-request
  "Builds a hosted-payment charge request. Card details are deliberately not
  accepted here; the Asaas invoice flow keeps raw card data outside this service."
  [command due-date]
  (let [customer-reference (get-in command [:customer :reference])]
    (when-not (and (string? customer-reference) (seq customer-reference)
                   (string? due-date) (seq due-date))
      (invalid-request!))
    {:method :post
     :path "/payments"
     :body {:customer customer-reference
            :billingType (billing-type (:method command))
            :value (amount->reais (:amount command))
            :dueDate due-date
            :externalReference (or (:provider-payment/external-reference command)
                                   (str (:payment/id command)))}
     ;; The Asaas API does not document an HTTP idempotency header for this
     ;; endpoint. The caller keeps this key in its provider-operation record for
     ;; reconciliation; it is not sent as a provider-specific header.
     :idempotency-key (operation-idempotency-key (:operation/id command) "create-payment")}))

(defn fetch-request [reference]
  {:method :get :path (str "/payments/" reference)})

(defn pix-qr-code-request [reference]
  {:method :get :path (str "/payments/" reference "/pixQrCode")})

(defn cancel-request [command]
  {:method :delete
   :path (str "/payments/" (:provider-payment/reference command))
   :idempotency-key (operation-idempotency-key (:operation/id command) "cancel-payment")})

(defn refund-request [command]
  (cond-> {:method :post
           :path (str "/payments/" (:provider-payment/reference command) "/refund")
           :body {}
           :idempotency-key (operation-idempotency-key (:operation/id command) "refund-payment")}
    (:refund/amount command)
    (assoc :body {:value (amount->reais (:refund/amount command))})))

(defn- canonical-status [asaas-status]
  (case asaas-status
    ("PENDING" "AWAITING_RISK_ANALYSIS" "OVERDUE" "DUNNING_REQUESTED"
     "REFUND_REQUESTED" "CHARGEBACK_REQUESTED" "CHARGEBACK_DISPUTE") :provider.status/processing
    ("CONFIRMED" "RECEIVED" "RECEIVED_IN_CASH") :provider.status/succeeded
    "REFUNDED" :provider.status/succeeded
    ("CANCELLED" "DELETED") :provider.status/cancelled
    (unexpected-response!)))

(defn payment->provider-result [{:keys [body request-id]}]
  (let [{:keys [id status]} body]
    (when-not (and (string? id) (string? status))
      (unexpected-response!))
    {:provider :asaas
     :provider-payment/reference id
     :provider-payment/status (canonical-status status)
     :provider-payment/raw-status status
     :provider-request-id request-id}))

(defn- expiration->instant [expiration]
  (when-not (string? expiration) (unexpected-response!))
  (try
    (Instant/parse expiration)
    (catch java.time.format.DateTimeParseException _
      (try
        (.toInstant (.atZone (LocalDateTime/parse (string/replace expiration " " "T"))
                             (ZoneId/of "America/Sao_Paulo")))
        (catch java.time.format.DateTimeParseException _ (unexpected-response!))))))

(defn- pix-action [{:keys [encodedImage payload expirationDate]}]
  (when-not (and (string? encodedImage) (seq encodedImage)
                 (string? payload) (seq payload))
    (unexpected-response!))
  {:action/type :pix/qr-code
   :action/payload payload
   ;; The canonical model accepts a QR representation. A data URI avoids
   ;; exposing Asaas's field name or full provider response to consumers.
   :action/qr-code-url (str "data:image/png;base64," encodedImage)
   :action/expires-at (expiration->instant expirationDate)})

(defn payment-with-pix-action->provider-result [payment-response pix-response]
  (let [result (payment->provider-result payment-response)]
    (assoc result
           :provider-payment/status (if (= :provider.status/processing (:provider-payment/status result))
                                      :provider.status/requires-action
                                      (:provider-payment/status result))
           :provider-payment/action (pix-action (:body pix-response)))))

(defn deleted-payment->provider-result [{:keys [body request-id]}]
  (when-not (and (true? (:deleted body)) (string? (:id body)))
    (unexpected-response!))
  {:provider :asaas
   :provider-payment/reference (:id body)
   :provider-payment/status :provider.status/cancelled
   :provider-payment/raw-status "DELETED"
   :provider-request-id request-id})

(defn refund->provider-result [{:keys [body request-id] :as response}]
  (assoc (payment->provider-result response)
         :provider-refund/reference (:id body)
         :provider-request-id request-id))
