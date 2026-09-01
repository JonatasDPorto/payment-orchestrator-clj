(ns payment-orchestrator-clj.merchant.datomic-repository-integration-test
  (:require [clojure.test :refer [deftest is testing]]
            [datomic.client.api :as d]
            [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.merchant.datomic-repository :as datomic-repository]
            [payment-orchestrator-clj.merchant.repository :as repository]))

(def merchant-a {:merchant/id "merchant-a"})
(def merchant-b {:merchant/id "merchant-b"})

(defn account [merchant-id account-id status]
  {:provider-account/id account-id
   :provider-account/merchant-id merchant-id
   :provider-account/provider :stripe
   :provider-account/status status
   :provider-account/secret-reference (str "vault://provider-accounts/" account-id)
   :provider-account/config {:display-name "Primary Stripe" :region "BR"}})

(defn configuration [merchant-id account-id]
  {:merchant-provider-configuration/id (random-uuid)
   :merchant-provider-configuration/merchant-id merchant-id
   :merchant-provider-configuration/provider-account-id account-id
   :merchant-provider-configuration/enabled true
   :merchant-provider-configuration/priority 10
   :merchant-provider-configuration/config {:currencies #{:BRL}
                                             :payment-methods #{:payment.method/pix}}})

(defn with-repositories [f]
  (support/with-test-database
    (fn [connection]
      (let [merchants (datomic-repository/new-merchant-repository connection)
            accounts (datomic-repository/new-provider-account-repository connection)
            configurations (datomic-repository/new-provider-configuration-repository connection)]
        (repository/save-merchant! merchants merchant-a {})
        (repository/save-merchant! merchants merchant-b {})
        (f connection accounts configurations)))))

(deftest provider-account-save-and-read-is-merchant-scoped
  (with-repositories
    (fn [_ accounts _]
      (repository/save-provider-account! accounts (account "merchant-a" "stripe-a" :active) {})
      (is (= {:provider-account/id "stripe-a"
              :provider-account/provider :stripe
              :provider-account/status :active
              :provider-account/secret-reference "vault://provider-accounts/stripe-a"
              :provider-account/config {:display-name "Primary Stripe" :region "BR"}
              :provider-account/merchant {:merchant/id "merchant-a"}}
             (select-keys (repository/find-provider-account accounts "merchant-a" "stripe-a")
                          [:provider-account/id :provider-account/provider :provider-account/status
                           :provider-account/secret-reference :provider-account/config :provider-account/merchant])))
      (is (nil? (repository/find-provider-account accounts "merchant-b" "stripe-a"))))))

(deftest inactive-provider-account-remains-persisted-and-identifiable
  (with-repositories
    (fn [_ accounts _]
      (repository/save-provider-account! accounts (account "merchant-a" "stripe-disabled" :inactive) {})
      (is (= :inactive (:provider-account/status
                         (repository/find-provider-account accounts "merchant-a" "stripe-disabled")))))))

(deftest provider-account-id-is-unique-and-cannot-change-merchant
  (with-repositories
    (fn [connection accounts _]
      (repository/save-provider-account! accounts (account "merchant-a" "stripe-a" :active) {})
      (repository/save-provider-account! accounts (assoc (account "merchant-a" "stripe-a" :inactive)
                                                         :provider-account/config {:display-name "Disabled Stripe"}) {})
      (is (= [[1]]
             (d/q '[:find (count ?account)
                    :in $ ?account-id
                    :where [?account :provider-account/id ?account-id]]
                  (d/db connection) "stripe-a")))
      (let [error (try
                    (repository/save-provider-account! accounts (account "merchant-b" "stripe-a" :active) {})
                    nil
                    (catch clojure.lang.ExceptionInfo error error))]
        (is (= :merchant-provider/account-merchant-immutable (:error/code (ex-data error))))))))

(deftest merchant-provider-configuration-save-and-read-is-merchant-scoped
  (with-repositories
    (fn [_ accounts configurations]
      (repository/save-provider-account! accounts (account "merchant-a" "stripe-a" :active) {})
      (let [saved (configuration "merchant-a" "stripe-a")]
        (repository/save-provider-configuration! configurations saved {})
        (is (= [{:merchant-provider-configuration/id (:merchant-provider-configuration/id saved)
                 :merchant-provider-configuration/enabled true
                 :merchant-provider-configuration/priority 10
                 :merchant-provider-configuration/config {:currencies #{:BRL}
                                                           :payment-methods #{:payment.method/pix}}
                 :merchant-provider-configuration/merchant {:merchant/id "merchant-a"}
                 :merchant-provider-configuration/provider-account {:provider-account/id "stripe-a"
                                                                     :provider-account/provider :stripe
                                                                     :provider-account/status :active}}]
               (mapv #(select-keys % [:merchant-provider-configuration/id :merchant-provider-configuration/enabled
                                      :merchant-provider-configuration/priority :merchant-provider-configuration/config
                                      :merchant-provider-configuration/merchant :merchant-provider-configuration/provider-account])
                     (repository/provider-configurations configurations "merchant-a"))))
        (is (= [] (repository/provider-configurations configurations "merchant-b")))))))

(deftest cross-tenant-provider-account-is-rejected-for-configuration
  (with-repositories
    (fn [_ accounts configurations]
      (repository/save-provider-account! accounts (account "merchant-a" "stripe-a" :active) {})
      (let [error (try
                    (repository/save-provider-configuration! configurations (configuration "merchant-b" "stripe-a") {})
                    nil
                    (catch clojure.lang.ExceptionInfo error error))]
        (is (= :merchant-provider/cross-tenant-account (:error/code (ex-data error))))))))

(deftest real-secrets-cannot-be-persisted
  (with-repositories
    (fn [connection accounts _]
      (let [real-secret "live-secret-must-never-reach-datomic"
            error (try
                    (repository/save-provider-account!
                     accounts
                     (assoc (account "merchant-a" "stripe-a" :active)
                            :provider-account/secret real-secret)
                     {})
                    nil
                    (catch clojure.lang.ExceptionInfo error error))]
        (is (= :merchant-provider/unsupported-field (:error/code (ex-data error))))
        (is (= [] (d/q '[:find ?account
                         :in $ ?reference
                         :where [?account :provider-account/secret-reference ?reference]]
                       (d/db connection) real-secret)))))))
