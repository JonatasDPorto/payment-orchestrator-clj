(ns payment-orchestrator-clj.api.payment
  "HTTP DTO validation and thin payment handlers."
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [malli.core :as m]
            [malli.error :as me]
            [payment-orchestrator-clj.payment.repository :as repository]
            [payment-orchestrator-clj.audit.repository :as audit]
            [payment-orchestrator-clj.ledger.repository :as ledger]
            [payment-orchestrator-clj.payment.service :as service])
  (:import [java.time Instant]
           [java.util UUID]))

(def create-payment-request
  [:map {:closed true}
   [:customerId [:and string? [:fn (complement clojure.string/blank?)]]]
   [:amount [:and int? [:fn pos?]]]
   [:currency [:enum "BRL"]]
   [:method [:enum "card" "pix" "boleto"]]
   [:pix {:optional true} [:map {:closed true}
                           [:taxId [:and string? [:fn (complement clojure.string/blank?)]]]
                           [:email [:and string? [:fn (complement clojure.string/blank?)]]]
                           [:name [:and string? [:fn (complement clojure.string/blank?)]]]]]
   [:boleto {:optional true}
    [:map {:closed true}
     [:taxId [:and string? [:fn (complement clojure.string/blank?)]]]
     [:email [:and string? [:fn (complement clojure.string/blank?)]]]
     [:name [:and string? [:fn (complement clojure.string/blank?)]]]
     [:address
      [:map {:closed true}
       [:line1 [:and string? [:fn (complement clojure.string/blank?)]]]
       [:city [:and string? [:fn (complement clojure.string/blank?)]]]
       [:state [:and string? [:fn (complement clojure.string/blank?)]]]
       [:postalCode [:and string? [:fn (complement clojure.string/blank?)]]]
       [:country [:= "BR"]]]]]]])

(defn- json-response [status body]
  {:status status
   :headers {"content-type" "application/json; charset=utf-8"}
   :body (json/write-str body)})

(defn- error-response [status code message details]
  (json-response status {:error {:code code :message message :details details}}))

(defn- decode-body [request]
  (json/read-str (slurp (:body request)) :key-fn keyword))

(defn- merchant-id [request]
  (or (get-in request [:headers "x-merchant-id"]) "default"))

(defn- request->command [request body]
  {:customer-id (:customerId request)
   :merchant-id (merchant-id body)
   :amount (:amount request)
   :currency (keyword (:currency request))
   :method (keyword "payment.method" (:method request))
   :pix (when-let [pix (:pix request)]
          {:tax-id (:taxId pix) :email (:email pix) :name (:name pix)})
   :boleto (when-let [boleto (:boleto request)]
             {:tax-id (:taxId boleto)
              :email (:email boleto)
              :name (:name boleto)
              :address {:line1 (get-in boleto [:address :line1])
                        :city (get-in boleto [:address :city])
                        :state (get-in boleto [:address :state])
                        :postal-code (get-in boleto [:address :postalCode])
                        :country (get-in boleto [:address :country])}})})

(defn- valid-pix-request? [body]
  (or (not= "pix" (:method body))
      (m/validate [:map {:closed true}
                   [:taxId [:and string? [:fn (complement clojure.string/blank?)]]]
                   [:email [:and string? [:fn (complement clojure.string/blank?)]]]
                   [:name [:and string? [:fn (complement clojure.string/blank?)]]]]
                  (:pix body))))

(defn- valid-boleto-request? [body]
  (or (not= "boleto" (:method body))
      (m/validate [:map {:closed true}
                   [:taxId [:and string? [:fn (complement clojure.string/blank?)]]]
                   [:email [:and string? [:fn (complement clojure.string/blank?)]]]
                   [:name [:and string? [:fn (complement clojure.string/blank?)]]]
                   [:address [:map {:closed true}
                              [:line1 [:and string? [:fn (complement clojure.string/blank?)]]]
                              [:city [:and string? [:fn (complement clojure.string/blank?)]]]
                              [:state [:and string? [:fn (complement clojure.string/blank?)]]]
                              [:postalCode [:and string? [:fn (complement clojure.string/blank?)]]]
                              [:country [:= "BR"]]]]]
                  (:boleto body))))

(defn- action-response [action]
  (case (:action/type action)
    :pix/qr-code (cond-> {:type "pix_qr_code"
                          :payload (:action/payload action)
                          :expiresAt (str (:action/expires-at action))}
                   (:action/qr-code-url action) (assoc :qrCodeUrl (:action/qr-code-url action))
                   (:action/hosted-instructions-url action) (assoc :hostedInstructionsUrl (:action/hosted-instructions-url action)))
    :client-secret {:type "client_secret" :value (:action/value action)}
    :boleto/voucher (cond-> {:type "boleto_voucher"
                             :number (:action/payload action)
                             :hostedVoucherUrl (:action/hosted-instructions-url action)
                             :expiresAt (str (:action/expires-at action))}
                      (:action/document-url action) (assoc :pdfUrl (:action/document-url action)))
    nil))

(defn payment-response
  ([payment] (payment-response payment nil))
  ([payment provider-result]
   (let [persisted-action (:payment/action payment)
         action (or (when-let [action-type (:payment-action/type persisted-action)]
                      {:action/type action-type
                       :action/payload (:payment-action/payload persisted-action)
                       :action/qr-code-url (:payment-action/qr-code-url persisted-action)
                       :action/hosted-instructions-url (:payment-action/hosted-instructions-url persisted-action)
                       :action/document-url (:payment-action/document-url persisted-action)
                       :action/expires-at (:payment-action/expires-at persisted-action)})
                    (when (contains? #{:payment.method/pix :payment.method/boleto} (:payment/method payment))
                      (:provider-payment/action provider-result)))]
     (cond-> {:id (str (:payment/id payment))
              :status (name (:payment/status payment))
              :amount (:payment/amount payment)
              :currency (name (:payment/currency payment))}
       action (assoc :action (action-response action))))))

(defn- request-context [request]
  (let [request-id (or (get-in request [:headers "x-request-id"])
                       (str (UUID/randomUUID)))]
    {:request-id request-id :correlation-id request-id :actor :actor/api-client
     :source :source/http :reason :reason/payment-create :event-type :event/payment-created}))

(defn- idempotency-key [request]
  (let [key (get-in request [:headers "idempotency-key"])]
    (when-not (string/blank? key) key)))

(defn create-payment-handler [dependencies]
  (fn [request]
    (let [body (try
                 (decode-body request)
                 (catch Exception _ ::invalid-json))]
      (if (= ::invalid-json body)
        (error-response 400 "invalid_json" "Request body must be valid JSON" {})
        (try
          (if-not (and (m/validate create-payment-request body) (valid-pix-request? body) (valid-boleto-request? body))
            (error-response 400 "invalid_payment" "Payment request is invalid"
                            {:validation (me/humanize (m/explain create-payment-request body))})
          (if-let [key (idempotency-key request)]
            (let [{:keys [outcome payment provider-result]} (service/create-payment-idempotently!
                                              dependencies (request->command body request) key (request-context request))]
              (case outcome
                :conflict (error-response 409 "idempotency_conflict"
                                          "Idempotency-Key was already used with a different request" {})
                (assoc (json-response (if (= :created outcome) 201 200) (payment-response payment provider-result))
                       :headers {"content-type" "application/json; charset=utf-8"
                                 "location" (str "/v1/payments/" (:payment/id payment))})))
            (error-response 400 "missing_idempotency_key" "Idempotency-Key header is required" {})))
          (catch clojure.lang.ExceptionInfo error
            (if (:provider/error? (ex-data error))
              (error-response 502 "provider_error" "Payment provider could not complete the operation" {})
              (error-response 400 "invalid_payment" "Payment request is invalid" (ex-data error)))))))))

(defn- as-of-point [request]
  (when-let [value (get-in request [:query-params "asOf"])]
    (try
      (if (re-matches #"\\d+" value)
        (Long/parseLong value)
        (Instant/parse value))
      (catch Exception _ ::invalid-as-of))))

(defn find-payment-handler [dependencies]
  (fn [request]
    (let [payment-id (try
                       (UUID/fromString (get-in request [:path-params :id]))
                       (catch IllegalArgumentException _ nil))
          point (as-of-point request)]
      (cond
        (= ::invalid-as-of point) (error-response 400 "invalid_as_of" "asOf must be an ISO-8601 instant or Datomic t" {})
        :else (let [payment (when payment-id
                               (if point
                                 (audit/payment-as-of (:audit dependencies) payment-id point)
                                 (repository/find-payment (:payments dependencies) payment-id)))]
                (if (and payment (= (or (:payment/merchant-id payment) "default") (merchant-id request)))
                  (json-response 200 (payment-response payment))
                  (error-response 404 "payment_not_found" "Payment was not found" {})))))))

(defn payment-history-handler [dependencies]
  (fn [request]
    (let [payment-id (try (UUID/fromString (get-in request [:path-params :id]))
                          (catch IllegalArgumentException _ nil))]
      (if-not (let [payment (and payment-id (repository/find-payment (:payments dependencies) payment-id))]
                (and payment (= (or (:payment/merchant-id payment) "default") (merchant-id request))) )
        (error-response 404 "payment_not_found" "Payment was not found" {})
        (json-response 200
                       {:paymentId (str payment-id)
                        :timeline (mapv (fn [event]
                                          {:status (name (:audit/status event))
                                           :change (if (:audit/added? event) "asserted" "retracted")
                                           :at (str (:audit/at event))
                                           :transaction (:audit/transaction event)
                                           :requestId (:audit/request-id event)
                                           :correlationId (:audit/correlation-id event)
                                           :actor (some-> (:audit/actor event) name)
                                           :source (some-> (:audit/source event) name)
                                           :reason (some-> (:audit/reason event) name)
                                           :eventType (some-> (:audit/event-type event) name)})
                                        (audit/payment-history (:audit dependencies) payment-id))})))))

(defn payment-ledger-handler [dependencies]
  (fn [request]
    (let [payment-id (try (UUID/fromString (get-in request [:path-params :id]))
                          (catch IllegalArgumentException _ nil))
          payment (when payment-id (repository/find-payment (:payments dependencies) payment-id))]
      (if (and payment (= (or (:payment/merchant-id payment) "default") (merchant-id request)))
        (json-response 200 {:paymentId (str payment-id)
                            :journals (mapv (fn [journal]
                                              {:id (str (:journal/id journal))
                                               :type (name (:journal/type journal))
                                               :postings (mapv (fn [posting]
                                                                 {:id (str (:posting/id posting))
                                                                  :account (get-in posting [:posting/account :ledger-account/code])
                                                                  :side (name (:posting/side posting))
                                                                  :amount (:posting/amount posting)
                                                                  :currency (name (:posting/currency posting))})
                                                               (:journal/postings journal))})
                                            (ledger/payment-journals (:ledger dependencies) payment-id))})
        (error-response 404 "payment_not_found" "Payment was not found" {})))))
