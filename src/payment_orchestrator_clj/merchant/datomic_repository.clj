(ns payment-orchestrator-clj.merchant.datomic-repository
  (:require [clojure.edn :as edn] [clojure.string :as string]
            [datomic.client.api :as d]
            [payment-orchestrator-clj.merchant.repository :as repository])
  (:import [java.time Instant] [java.util Date]))

(def ^:private secret-key-pattern #"(?i)(secret|token|password|api[_-]?key)")
(def ^:private provider-account-fields
  #{:provider-account/id :provider-account/merchant-id :provider-account/provider
    :provider-account/status :provider-account/secret-reference :provider-account/webhook-identity :provider-account/config
    :provider-account/created-at :provider-account/updated-at})
(def ^:private provider-configuration-fields
  #{:merchant-provider-configuration/id :merchant-provider-configuration/merchant-id
    :merchant-provider-configuration/provider-account-id :merchant-provider-configuration/enabled
    :merchant-provider-configuration/priority :merchant-provider-configuration/config
    :merchant-provider-configuration/created-at :merchant-provider-configuration/updated-at})

(defn- sensitive? [value]
  (cond
    (map? value) (some (fn [[key nested-value]]
                         (or (re-find secret-key-pattern (str key))
                             (sensitive? nested-value)))
                       value)
    (sequential? value) (some sensitive? value)
    :else false))
(defn- safe-config! [config]
  (when (sensitive? config) (throw (ex-info "Provider configuration may not contain secrets" {:error/code :merchant-provider/sensitive-config}))) config)
(defn- require-fields! [entity allowed-fields entity-name]
  (when-let [unknown-field (first (remove allowed-fields (keys entity)))]
    ;; Do not include the value in exception data: an unknown field may be a secret.
    (throw (ex-info (str entity-name " contains an unsupported field")
                    {:error/code :merchant-provider/unsupported-field
                     :field unknown-field}))))
(defn- non-blank-string? [value] (and (string? value) (not (string/blank? value))))
(defn- valid-account! [account]
  (require-fields! account provider-account-fields "Provider account")
  (when-not (and (non-blank-string? (:provider-account/id account))
                 (non-blank-string? (:provider-account/merchant-id account))
                 (keyword? (:provider-account/provider account))
                 (contains? #{:active :inactive} (:provider-account/status account))
                 (non-blank-string? (:provider-account/secret-reference account))
                 (map? (:provider-account/config account)))
    (throw (ex-info "Invalid provider account" {:error/code :merchant-provider/invalid-provider-account})))
  (safe-config! (:provider-account/config account)))
(defn- valid-configuration! [configuration]
  (require-fields! configuration provider-configuration-fields "Merchant provider configuration")
  (when-not (and (instance? java.util.UUID (:merchant-provider-configuration/id configuration))
                 (non-blank-string? (:merchant-provider-configuration/merchant-id configuration))
                 (non-blank-string? (:merchant-provider-configuration/provider-account-id configuration))
                 (boolean? (:merchant-provider-configuration/enabled configuration))
                 (or (nil? (:merchant-provider-configuration/priority configuration))
                     (integer? (:merchant-provider-configuration/priority configuration)))
                 (map? (:merchant-provider-configuration/config configuration)))
    (throw (ex-info "Invalid merchant provider configuration" {:error/code :merchant-provider/invalid-configuration})))
  (safe-config! (:merchant-provider-configuration/config configuration)))
(defn- tx-meta [context] (cond-> {:db/id "datomic.tx"} (:request-id context) (assoc :tx/request-id (:request-id context)) (:actor context) (assoc :tx/actor (:actor context))))
(defn- decode [entity] (cond-> entity (:provider-account/config entity) (update :provider-account/config edn/read-string) (:merchant-provider-configuration/config entity) (update :merchant-provider-configuration/config edn/read-string)))

(defrecord DatomicMerchantRepository [connection]
  repository/MerchantRepository
  (save-merchant! [_ merchant context]
    (d/transact connection {:tx-data [(assoc merchant :merchant/created-at (Date/from (or (:merchant/created-at merchant) (Instant/now)))) (tx-meta context)]}) merchant)
  (find-merchant [_ merchant-id] (d/pull (d/db connection) '[:merchant/id :merchant/created-at] [:merchant/id merchant-id])))

(defrecord DatomicMerchantProviderRepository [connection]
  repository/ProviderAccountRepository
  (save-provider-account! [_ account context]
    (valid-account! account)
    (let [merchant-id (:provider-account/merchant-id account) now (Date/from (or (:provider-account/updated-at account) (Instant/now)))]
      (when-not (d/pull (d/db connection) [:merchant/id] [:merchant/id merchant-id]) (throw (ex-info "Merchant not found" {:error/code :merchant/not-found})))
      (let [existing (d/pull (d/db connection) '[:provider-account/created-at {:provider-account/merchant [:merchant/id]}] [:provider-account/id (:provider-account/id account)])]
        (when (and existing (not= merchant-id (get-in existing [:provider-account/merchant :merchant/id])))
          (throw (ex-info "Provider account merchant is immutable" {:error/code :merchant-provider/account-merchant-immutable})))
        (d/transact connection {:tx-data [(-> account
                                           (dissoc :provider-account/merchant-id)
                                           (assoc :provider-account/merchant [:merchant/id merchant-id]
                                                  :provider-account/config (pr-str (:provider-account/config account))
                                                  :provider-account/created-at (or (:provider-account/created-at existing) now)
                                                  :provider-account/updated-at now))
                                      (tx-meta context)]})
        account)))
  (find-provider-account [_ merchant-id account-id]
    (some-> (d/pull (d/db connection) '[:provider-account/id :provider-account/provider :provider-account/status :provider-account/secret-reference :provider-account/webhook-identity :provider-account/config :provider-account/created-at :provider-account/updated-at {:provider-account/merchant [:merchant/id]}] [:provider-account/id account-id])
            (as-> a (when (= merchant-id (get-in a [:provider-account/merchant :merchant/id])) a)) decode))
  (find-provider-account-by-webhook-identity [_ provider webhook-identity]
    (some-> (d/pull (d/db connection) '[:provider-account/id :provider-account/provider :provider-account/status :provider-account/secret-reference :provider-account/webhook-identity :provider-account/config :provider-account/created-at :provider-account/updated-at {:provider-account/merchant [:merchant/id]}]
                     [:provider-account/webhook-identity webhook-identity])
            (as-> account (when (= provider (:provider-account/provider account)) account))
            decode))
  repository/MerchantProviderConfigurationRepository
  (save-provider-configuration! [this configuration context]
    (valid-configuration! configuration)
    (let [merchant-id (:merchant-provider-configuration/merchant-id configuration) account-id (:merchant-provider-configuration/provider-account-id configuration)]
      (when-not (repository/find-provider-account this merchant-id account-id) (throw (ex-info "Provider account does not belong to merchant" {:error/code :merchant-provider/cross-tenant-account})))
      (let [now (Date/from (Instant/now))
            existing (d/pull (d/db connection)
                             '[:merchant-provider-configuration/created-at
                               {:merchant-provider-configuration/merchant [:merchant/id]}
                               {:merchant-provider-configuration/provider-account [:provider-account/id]}]
                             [:merchant-provider-configuration/id (:merchant-provider-configuration/id configuration)])]
        (when (and existing
                   (not= [merchant-id account-id]
                         [(get-in existing [:merchant-provider-configuration/merchant :merchant/id])
                          (get-in existing [:merchant-provider-configuration/provider-account :provider-account/id])]))
          (throw (ex-info "Merchant provider configuration ownership is immutable"
                          {:error/code :merchant-provider/configuration-ownership-immutable})))
        (d/transact connection {:tx-data [(-> configuration
                                           (dissoc :merchant-provider-configuration/merchant-id :merchant-provider-configuration/provider-account-id)
                                           (assoc :merchant-provider-configuration/merchant [:merchant/id merchant-id]
                                                  :merchant-provider-configuration/provider-account [:provider-account/id account-id]
                                                  :merchant-provider-configuration/config (pr-str (:merchant-provider-configuration/config configuration))
                                                  :merchant-provider-configuration/created-at (or (:merchant-provider-configuration/created-at existing) now)
                                                  :merchant-provider-configuration/updated-at now))
                                      (tx-meta context)]})
        configuration)))
  (provider-configurations [_ merchant-id]
    (->> (d/q '[:find (pull ?c pattern) :in $ ?merchant pattern :where [?c :merchant-provider-configuration/merchant ?m] [?m :merchant/id ?merchant]] (d/db connection) merchant-id '[:merchant-provider-configuration/id :merchant-provider-configuration/enabled :merchant-provider-configuration/priority :merchant-provider-configuration/config :merchant-provider-configuration/created-at :merchant-provider-configuration/updated-at {:merchant-provider-configuration/merchant [:merchant/id]} {:merchant-provider-configuration/provider-account [:provider-account/id :provider-account/provider :provider-account/status]}]) (mapv (comp decode first)))))

(defn new-merchant-repository [connection] (->DatomicMerchantRepository connection))
(defn new-provider-account-repository [connection] (->DatomicMerchantProviderRepository connection))
(defn new-provider-configuration-repository [connection] (->DatomicMerchantProviderRepository connection))
