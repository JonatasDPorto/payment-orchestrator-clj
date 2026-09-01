(ns payment-orchestrator-clj.merchant.provider-candidates-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.merchant.provider-runtime :as runtime]
            [payment-orchestrator-clj.merchant.repository :as repository]
            [payment-orchestrator-clj.payment.repository :as payment-repository]
            [payment-orchestrator-clj.payment.service :as payment-service]
            [payment-orchestrator-clj.provider.port :as port]
            [payment-orchestrator-clj.provider.routing :as routing])
  (:import [java.time Instant]
           [java.util UUID]))

(defrecord Accounts [entries]
  repository/ProviderAccountRepository
  (save-provider-account! [_ _ _] (throw (UnsupportedOperationException.)))
  (find-provider-account [_ merchant-id account-id] (get entries [merchant-id account-id])))

(defrecord Configurations [entries]
  repository/MerchantProviderConfigurationRepository
  (save-provider-configuration! [_ _ _] (throw (UnsupportedOperationException.)))
  (provider-configurations [_ merchant-id] (get entries merchant-id [])))

(defrecord Payments [values idempotency]
  payment-repository/PaymentRepository
  (save-payment! [this payment] (payment-repository/save-payment! this payment {}))
  (save-payment! [_ payment _] (swap! values assoc (:payment/id payment) payment) payment)
  (create-payment-idempotently! [_ payment record _]
    (if-let [existing (get @idempotency (:idempotency/key record))]
      {:outcome :replayed :payment (:payment existing)}
      (do (swap! values assoc (:payment/id payment) payment)
          (swap! idempotency assoc (:idempotency/key record) {:payment payment})
          {:outcome :created :payment payment})))
  (record-provider-result! [_ payment _ _] (swap! values assoc (:payment/id payment) payment) payment)
  (find-payment [_ payment-id] (get @values payment-id)))

(defn account [merchant account-id provider status]
  {:provider-account/id account-id
   :provider-account/merchant {:merchant/id merchant}
   :provider-account/provider provider
   :provider-account/status status
   :provider-account/secret-reference (str "env://" account-id)
   :provider-account/config {:account-id account-id}})

(defn configuration [account config]
  {:merchant-provider-configuration/enabled true
   :merchant-provider-configuration/config config
   :merchant-provider-configuration/provider-account
   (select-keys account [:provider-account/id :provider-account/provider :provider-account/status])})

(def catalog
  [{:provider :stripe :available? true :cost 50
    :currencies #{:BRL :USD}
    :capabilities #{:payment/create :method/card :method/pix}}
   {:provider :asaas :available? true :cost 1
    :currencies #{:BRL}
    :capabilities #{:payment/create :method/card :method/pix :method/boleto}}])

(defn merchant-runtime [accounts configurations]
  (runtime/new-runtime {:provider-accounts (->Accounts accounts)
                        :provider-configurations (->Configurations configurations)
                        :secret-resolver (runtime/new-local-secret-resolver {:environment {}})}))

(defn candidates [merchant runtime]
  (runtime/provider-candidates runtime {:merchant-id merchant} catalog))

(deftest merchant-candidates-exclude-inactive-and-other-tenant-accounts
  (let [stripe-a (account "merchant-a" "stripe-a" :stripe :active)
        asaas-a (account "merchant-a" "asaas-a" :asaas :inactive)
        asaas-b (account "merchant-b" "asaas-b" :asaas :active)
        resolver (merchant-runtime
                  {["merchant-a" "stripe-a"] stripe-a
                   ["merchant-a" "asaas-a"] asaas-a
                   ["merchant-b" "asaas-b"] asaas-b}
                  {"merchant-a" [(configuration stripe-a {:currencies #{:BRL}
                                                           :payment-methods #{:payment.method/card}})
                                 (configuration asaas-a {})]
                   "merchant-b" [(configuration asaas-b {:currencies #{:BRL}
                                                          :payment-methods #{:payment.method/pix}})]})]
    (is (= [:stripe] (mapv :provider (candidates "merchant-a" resolver))))
    (is (= [:asaas] (mapv :provider (candidates "merchant-b" resolver))))
    (is (= :stripe
           (:provider (routing/select-provider
                       {:merchant-id "merchant-a" :currency :BRL :method :payment.method/card
                        :routing {:strategy :routing.strategy/lowest-cost}}
                       (candidates "merchant-a" resolver)))))
    (is (= :asaas
           (:provider (routing/select-provider
                       {:merchant-id "merchant-b" :currency :BRL :method :payment.method/pix
                        :routing {:by-merchant {"merchant-b" :asaas}}}
                       (candidates "merchant-b" resolver)))))))

(deftest candidates-preserve-m18-method-and-currency-compatibility
  (let [stripe-a (account "merchant-a" "stripe-a" :stripe :active)
        resolver (merchant-runtime {["merchant-a" "stripe-a"] stripe-a}
                                   {"merchant-a" [(configuration stripe-a {:currencies #{:BRL}
                                                                            :payment-methods #{:payment.method/card}})]})
        available (candidates "merchant-a" resolver)]
    (is (= :stripe (:provider (routing/select-provider
                               {:merchant-id "merchant-a" :currency :BRL :method :payment.method/card
                                :routing {:by-payment-method {:payment.method/card :stripe}}}
                               available))))
    (is (= :stripe (:provider (routing/select-provider
                               {:merchant-id "merchant-a" :currency :BRL :method :payment.method/card
                                :routing {:by-currency {:BRL :stripe}}}
                               available))))
    (is (thrown? clojure.lang.ExceptionInfo
                 (routing/select-provider {:merchant-id "merchant-a" :currency :USD :method :payment.method/card
                                           :routing {:default-provider :stripe}} available)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (routing/select-provider {:merchant-id "merchant-a" :currency :BRL :method :payment.method/pix
                                           :routing {:default-provider :stripe}} available)))))

(deftest missing-and-cross-tenant-configurations-produce-no-candidates
  (let [account-b (account "merchant-b" "asaas-b" :asaas :active)
        cross-tenant-config (configuration account-b {})
        resolver (merchant-runtime {["merchant-b" "asaas-b"] account-b}
                                   {"merchant-a" [cross-tenant-config]})]
    (is (= [] (candidates "merchant-a" resolver)))
    (is (= [] (candidates "merchant-c" resolver)))))

(deftest payment-execution-uses-the-selected-merchants-account-runtime
  (let [stripe-a (account "merchant-a" "stripe-a" :stripe :active)
        received (atom nil)
        gateway (reify port/PaymentGateway
                  (capabilities [_] #{:payment/create :method/card})
                  (create-payment! [_ command]
                    {:provider :stripe :provider-payment/reference "stripe-a-payment"
                     :provider-payment/status :provider.status/processing
                     :provider-payment/raw-status "PROCESSING"})
                  (fetch-payment [_ _] nil)
                  (cancel-payment! [_ _] nil)
                  (refund-payment! [_ _] nil))
        resolver (runtime/new-runtime
                  {:provider-accounts (->Accounts {["merchant-a" "stripe-a"] stripe-a})
                   :provider-configurations (->Configurations {"merchant-a" [(configuration stripe-a {})]})
                   :secret-resolver (runtime/new-local-secret-resolver {:environment {"stripe-a" "credential-a"}})
                   :gateway-factories {:stripe #(do (reset! received %) gateway)}})
        result (payment-service/create-payment-idempotently!
                {:payments (->Payments (atom {}) (atom {}))
                 :merchant-provider-runtime resolver
                 :provider-catalog catalog
                 :routing {:default-provider :stripe}
                 :clock #(Instant/parse "2026-09-01T12:00:00Z")
                 :id-generator #(UUID/fromString "aa1b6fa6-1ed5-4211-a9fd-fb4e1dfefa0d")}
                {:merchant-id "merchant-a" :customer-id "customer-a" :amount 100
                 :currency :BRL :method :payment.method/card}
                "merchant-a-key" {})]
    (is (= :created (:outcome result)))
    (is (= :stripe (get-in result [:provider-result :provider])))
    (is (= "credential-a" (:credential @received)))
    (is (= "stripe-a" (get-in @received [:provider-account :provider-account/id])))))
