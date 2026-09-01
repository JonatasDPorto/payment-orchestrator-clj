(ns payment-orchestrator-clj.merchant.provider-runtime-test
  (:require [clojure.test :refer [deftest is testing]]
            [payment-orchestrator-clj.merchant.provider-runtime :as runtime]
            [payment-orchestrator-clj.merchant.repository :as repository]))

(defrecord InMemoryProviderAccounts [accounts]
  repository/ProviderAccountRepository
  (save-provider-account! [_ _ _] (throw (UnsupportedOperationException.)))
  (find-provider-account [_ merchant-id account-id]
    (get accounts [merchant-id account-id])))

(defrecord InMemoryProviderConfigurations [configurations]
  repository/MerchantProviderConfigurationRepository
  (save-provider-configuration! [_ _ _] (throw (UnsupportedOperationException.)))
  (provider-configurations [_ merchant-id]
    (get configurations merchant-id [])))

(defn account [merchant-id account-id provider status reference config]
  {:provider-account/id account-id
   :provider-account/merchant {:merchant/id merchant-id}
   :provider-account/provider provider
   :provider-account/status status
   :provider-account/secret-reference reference
   :provider-account/config config})

(defn configuration [account enabled config]
  {:merchant-provider-configuration/id (random-uuid)
   :merchant-provider-configuration/enabled enabled
   :merchant-provider-configuration/config config
   :merchant-provider-configuration/provider-account
   (select-keys account [:provider-account/id :provider-account/provider :provider-account/status])})

(defn test-runtime
  [{:keys [accounts configurations environment gateway-factories]}]
  (runtime/new-runtime
   {:provider-accounts (->InMemoryProviderAccounts accounts)
    :provider-configurations (->InMemoryProviderConfigurations configurations)
    :secret-resolver (runtime/new-local-secret-resolver {:environment environment})
    :gateway-factories gateway-factories}))

(defn error-code [thunk]
  (try
    (thunk)
    nil
    (catch clojure.lang.ExceptionInfo error
      (:error/code (ex-data error)))))

(deftest resolves-each-merchants-explicit-provider-runtime
  (let [stripe-account (account "merchant-a" "stripe-a" :stripe :active "env://MERCHANT_A_STRIPE"
                                {:timeout-ms 1000 :region "BR"})
        asaas-account (account "merchant-b" "asaas-b" :asaas :active "env://MERCHANT_B_ASAAS"
                               {:timeout-ms 2000 :base-url "https://asaas.example/v3"})
        resolver (test-runtime
                  {:accounts { ["merchant-a" "stripe-a"] stripe-account
                              ["merchant-b" "asaas-b"] asaas-account}
                   :configurations {"merchant-a" [(configuration stripe-account true {:payment-method "pix"})]
                                    "merchant-b" [(configuration asaas-account true {:payment-method "boleto"})]}
                   :environment {"MERCHANT_A_STRIPE" "credential-a"
                                 "MERCHANT_B_ASAAS" "credential-b"}})
        a (runtime/resolve-provider-runtime resolver {:merchant-id "merchant-a"} :stripe)
        b (runtime/resolve-provider-runtime resolver {:merchant-id "merchant-b"} :asaas)]
    (is (= "credential-a" (:credential a)))
    (is (= {:timeout-ms 1000 :region "BR" :payment-method "pix"} (:runtime-config a)))
    (is (= "credential-b" (:credential b)))
    (is (= {:timeout-ms 2000 :base-url "https://asaas.example/v3" :payment-method "boleto"} (:runtime-config b)))
    (is (not= (:credential a) (:credential b)))
    (is (not= (:runtime-config a) (:runtime-config b)))))

(deftest passes-the-resolved-context-to-the-gateway-factory
  (let [provider-account (account "merchant-a" "stripe-a" :stripe :active "env://STRIPE_A" {:timeout-ms 1000})
        seen (atom nil)
        resolver (test-runtime {:accounts {["merchant-a" "stripe-a"] provider-account}
                                :configurations {"merchant-a" [(configuration provider-account true {})]}
                                :environment {"STRIPE_A" "credential-a"}
                                :gateway-factories {:stripe #(reset! seen %) }})]
    (is (= "credential-a" (:credential (runtime/create-gateway resolver {:merchant-id "merchant-a"
                                                                            :request-id "request-a"} :stripe))))
    (is (= {:merchant-id "merchant-a" :request-id "request-a"} (:merchant-context @seen)))
    (is (= "stripe-a" (get-in @seen [:provider-account :provider-account/id])))))

(deftest rejects-inactive-accounts-and-missing-or-incompatible-configurations
  (let [inactive (account "merchant-a" "stripe-a" :stripe :inactive "env://STRIPE_A" {})
        active (assoc inactive :provider-account/status :active)
        inactive-runtime (test-runtime {:accounts {["merchant-a" "stripe-a"] inactive}
                                        :configurations {"merchant-a" [(configuration inactive true {})]}
                                        :environment {"STRIPE_A" "credential-a"}})
        missing-config-runtime (test-runtime {:accounts {} :configurations {} :environment {}})
        incompatible-runtime (test-runtime {:accounts {["merchant-a" "stripe-a"] (assoc active :provider-account/provider :asaas)}
                                             :configurations {"merchant-a" [(configuration active true {})]}
                                             :environment {"STRIPE_A" "credential-a"}})]
    (is (= :merchant-provider/account-inactive
           (error-code #(runtime/resolve-provider-runtime inactive-runtime {:merchant-id "merchant-a"} :stripe))))
    (is (= :merchant-provider/configuration-not-found
           (error-code #(runtime/resolve-provider-runtime missing-config-runtime {:merchant-id "merchant-a"} :stripe))))
    (is (= :merchant-provider/provider-incompatible
           (error-code #(runtime/resolve-provider-runtime incompatible-runtime {:merchant-id "merchant-a"} :stripe))))))

(deftest rejects-an-unresolvable-secret-reference
  (let [provider-account (account "merchant-a" "stripe-a" :stripe :active "env://MISSING" {})
        resolver (test-runtime {:accounts {["merchant-a" "stripe-a"] provider-account}
                                :configurations {"merchant-a" [(configuration provider-account true {})]}
                                :environment {}})]
    (is (= :merchant-provider/secret-not-found
           (error-code #(runtime/resolve-provider-runtime resolver {:merchant-id "merchant-a"} :stripe))))))

(deftest local-secret-resolver-supports-injected-development-config
  (let [resolver (runtime/new-local-secret-resolver
                  {:config {:development {:merchant-a "credential-from-config"}}})]
    (is (= "credential-from-config"
           (runtime/resolve-secret resolver "config://development/merchant-a" {:merchant-id "merchant-a"})))))

(deftest concurrent-merchant-resolution-does-not-leak-credentials
  (let [stripe-account (account "merchant-a" "stripe-a" :stripe :active "env://STRIPE_A" {:merchant "a"})
        asaas-account (account "merchant-b" "asaas-b" :asaas :active "env://ASAAS_B" {:merchant "b"})
        resolver (test-runtime {:accounts {["merchant-a" "stripe-a"] stripe-account
                                           ["merchant-b" "asaas-b"] asaas-account}
                                :configurations {"merchant-a" [(configuration stripe-account true {})]
                                                 "merchant-b" [(configuration asaas-account true {})]}
                                :environment {"STRIPE_A" "credential-a" "ASAAS_B" "credential-b"}})
        resolutions (->> (range 100)
                         (map (fn [n]
                                (future
                                  (if (even? n)
                                    (runtime/resolve-provider-runtime resolver {:merchant-id "merchant-a"} :stripe)
                                    (runtime/resolve-provider-runtime resolver {:merchant-id "merchant-b"} :asaas)))))
                         (map deref)
                         doall)]
    (is (every? #(or (= ["merchant-a" "credential-a" {:merchant "a"}]
                        [(get-in % [:merchant-context :merchant-id]) (:credential %) (:runtime-config %)])
                     (= ["merchant-b" "credential-b" {:merchant "b"}]
                        [(get-in % [:merchant-context :merchant-id]) (:credential %) (:runtime-config %)]))
                resolutions))))
