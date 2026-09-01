(ns payment-orchestrator-clj.provider.asaas-sandbox-integration-test
  (:require [clojure.string :as string]
            [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.provider.asaas.adapter :as asaas]
            [payment-orchestrator-clj.provider.asaas.client :as client]
            [payment-orchestrator-clj.provider.port :as port])
  (:import [java.time LocalDate] [java.util UUID]))

(def ^:private sandbox-url "https://api-sandbox.asaas.com/v3")
(def ^:private customer-external-reference "payment-orchestrator-clj-m8-sandbox-customer-v1")
(defn- env! [name] (let [value (System/getenv name)] (when (string/blank? value) (throw (ex-info "Missing sandbox environment variable" {:environment-variable name}))) value))
(defn- customer! [c]
  (let [customers (get-in (client/request! c {:method :get :path "/customers?limit=100"}) [:body :data])
        customer (or (some #(when (= customer-external-reference (:externalReference %)) %) customers)
                     (some #(when (= "24971563792" (:cpfCnpj %)) %) customers))]
    (if customer
    {:customer customer :customer-outcome :reused}
    {:customer (:body (client/request! c {:method :post :path "/customers" :body {:name "Payment Orchestrator M8 Sandbox" :cpfCnpj "24971563792" :externalReference customer-external-reference :notificationDisabled true}})) :customer-outcome :created})))
(defn sandbox-flow! []
  (let [api-key (env! "ASAAS_API_KEY") base-url (env! "ASAAS_BASE_URL")]
    (when-not (= sandbox-url base-url) (throw (ex-info "Asaas Sandbox base URL is required" {:configured-base-url base-url})))
    (let [c (client/new-client {:api-key api-key :base-url base-url :timeout-ms 60000}) {:keys [customer customer-outcome]} (customer! c) payment-id (UUID/randomUUID)
          gateway (asaas/new-gateway {:environment {"ASAAS_API_KEY" api-key "ASAAS_BASE_URL" base-url} :base-url-env "ASAAS_BASE_URL" :due-date (str (.plusDays (LocalDate/now) 1))})
          external-reference (str "payment-orchestrator-clj:m19-pix-sandbox:" payment-id)
          created (port/create-payment! gateway {:operation/id (UUID/randomUUID) :payment/id payment-id :provider-payment/external-reference external-reference :amount 12990 :currency :BRL :method :payment.method/pix :customer {:reference (:id customer)}})
          reference (:provider-payment/reference created) fetched (port/fetch-payment gateway reference)
          raw (:body (client/request! c {:method :get :path (str "/payments/" reference)}))]
      (let [checks {:customer-id (string? (:id customer)) :provider-reference (= reference (:id raw))
                    :external-reference (= external-reference (:externalReference raw)) :amount (== 129.90 (:value raw))
                    :provider (= :asaas (:provider created)) :canonical-status (= (:provider-payment/status created) (:provider-payment/status fetched))
                    :pix-action (= :pix/qr-code (get-in created [:provider-payment/action :action/type]))
                    :pix-payload (string? (get-in created [:provider-payment/action :action/payload]))
                    :pix-qr (string? (get-in created [:provider-payment/action :action/qr-code-url]))
                    :pix-expiration (instance? java.time.Instant (get-in created [:provider-payment/action :action/expires-at]))
                    :fetch-action (= (:provider-payment/action created) (:provider-payment/action fetched))}]
        (when-not (every? true? (vals checks))
          (throw (ex-info "Sandbox response failed canonical validation" {:provider :asaas :provider-reference reference :checks checks :raw-status (:status raw)}))))
      {:customer-outcome customer-outcome :customer-reference (:id customer) :payment-reference reference :canonical-status (:provider-payment/status fetched)})))
(defn audit-sandbox-payments! []
  (let [api-key (env! "ASAAS_API_KEY") base-url (env! "ASAAS_BASE_URL")
        c (client/new-client {:api-key api-key :base-url base-url :timeout-ms 60000})
        customers (filter #(= customer-external-reference (:externalReference %))
                          (get-in (client/request! c {:method :get :path "/customers?limit=100"}) [:body :data]))]
    (when-not (seq customers) (throw (ex-info "Sandbox customer was not found" {})))
    (let [payments (mapcat #(get-in (client/request! c {:method :get :path (str "/payments?customer=" (:id %) "&limit=100")}) [:body :data]) customers)
          test-payments (filter #(and (= "PIX" (:billingType %)) (= 129.90M (bigdec (:value %))) (string? (:externalReference %))) payments)]
      {:customer-references (mapv :id customers)
       :payment-count (count test-payments)
       :payments (mapv #(select-keys % [:id :externalReference :status :value]) test-payments)})))
(defn cleanup-sandbox-payments! []
  (let [api-key (env! "ASAAS_API_KEY") base-url (env! "ASAAS_BASE_URL")
        gateway (asaas/new-gateway {:environment {"ASAAS_API_KEY" api-key "ASAAS_BASE_URL" base-url} :base-url-env "ASAAS_BASE_URL"})
        payments (:payments (audit-sandbox-payments!))]
    {:cancelled-references
     (mapv (fn [{:keys [id]}]
             (:provider-payment/reference
              (port/cancel-payment! gateway {:operation/id (UUID/randomUUID) :provider-payment/reference id})))
           payments)}))
(deftest ^:integration asaas-sandbox-customer-payment-fetch-flow
  (let [result (sandbox-flow!)]
    (is (contains? #{:created :reused} (:customer-outcome result)))
    (is (string? (:customer-reference result)))
    (is (string? (:payment-reference result)))
    (is (contains? #{:provider.status/requires-action :provider.status/succeeded} (:canonical-status result)))
    (println "ASAAS_SANDBOX_RESULT" result)))
