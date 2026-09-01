(ns payment-orchestrator-clj.merchant.provider-runtime
  "Merchant-scoped provider runtime resolution. This boundary owns no global
  credentials: every resolution receives its merchant context explicitly."
  (:require [clojure.set :as set]
            [clojure.string :as string]
            [payment-orchestrator-clj.merchant.repository :as repository]))

(defprotocol SecretResolver
  (resolve-secret [resolver secret-reference context]))

(defn- missing-secret! [reference]
  (throw (ex-info "Provider secret reference could not be resolved"
                  {:error/code :merchant-provider/secret-not-found
                   :secret-reference reference})))

(defn- config-value [config path]
  (reduce (fn [value segment]
            (when (map? value)
              (or (get value segment) (get value (keyword segment)))))
          config path))

(defrecord LocalSecretResolver [environment config]
  SecretResolver
  (resolve-secret [_ reference _]
    (let [[scheme target] (string/split reference #"://" 2)
          value (case scheme
                  "env" (get environment target)
                  "config" (config-value config (string/split target #"/"))
                  nil)]
      (if (and (string? value) (not (string/blank? value)))
        value
        (missing-secret! reference)))))

(defn new-local-secret-resolver
  "Development-only resolver for injected environment/config maps.
  It deliberately does not call System/getenv during a request."
  [{:keys [environment config] :or {environment {} config {}}}]
  (->LocalSecretResolver environment config))

(defn- resolution-error! [code]
  (throw (ex-info "Merchant provider runtime cannot be resolved" {:error/code code})))

(defn- active-configuration-for [configurations provider]
  (let [matches (filter #(= provider (get-in % [:merchant-provider-configuration/provider-account :provider-account/provider]))
                        configurations)]
    (cond
      (empty? matches) (resolution-error! :merchant-provider/configuration-not-found)
      (> (count matches) 1) (resolution-error! :merchant-provider/ambiguous-configuration)
      (not (:merchant-provider-configuration/enabled (first matches)))
      (resolution-error! :merchant-provider/configuration-disabled)
      :else (first matches))))

(defrecord MerchantProviderRuntime [provider-accounts provider-configurations secret-resolver gateway-factories])

(defn new-runtime
  "Creates the explicit application boundary used to obtain one merchant's
  provider credential and non-sensitive runtime configuration. Gateway factories
  receive a per-call context and must not mutate global configuration."
  [{:keys [provider-accounts provider-configurations secret-resolver gateway-factories]}]
  (when-not (and provider-accounts provider-configurations secret-resolver)
    (throw (ex-info "Merchant provider runtime dependencies are required"
                    {:error/code :merchant-provider/missing-runtime-dependency})))
  (->MerchantProviderRuntime provider-accounts provider-configurations secret-resolver (or gateway-factories {})))

(defn resolve-provider-runtime
  [runtime {:keys [merchant-id] :as merchant-context} provider]
  (when-not (and (string? merchant-id) (not (string/blank? merchant-id)))
    (resolution-error! :merchant-provider/missing-merchant-context))
  (let [configuration (active-configuration-for
                       (repository/provider-configurations (:provider-configurations runtime) merchant-id)
                       provider)
        account-id (get-in configuration [:merchant-provider-configuration/provider-account :provider-account/id])
        account (repository/find-provider-account (:provider-accounts runtime) merchant-id account-id)]
    (when-not account (resolution-error! :merchant-provider/account-not-found))
    (when-not (= provider (:provider-account/provider account))
      (resolution-error! :merchant-provider/provider-incompatible))
    (when-not (= :active (:provider-account/status account))
      (resolution-error! :merchant-provider/account-inactive))
    (let [credential (resolve-secret (:secret-resolver runtime)
                                     (:provider-account/secret-reference account)
                                     {:merchant-context merchant-context
                                      :provider provider
                                      :provider-account-id account-id})]
      {:merchant-context merchant-context
       :provider provider
       :provider-account account
       :provider-configuration configuration
       :credential credential
       :runtime-config (merge (:provider-account/config account)
                              (:merchant-provider-configuration/config configuration))})))

(defn- configured-method-capabilities [methods]
  (when (seq methods)
    (set (map (fn [method] (keyword "method" (name method))) methods))))

(defn- catalog-by-provider [provider-catalog]
  (into {} (map (juxt :provider identity) (if (map? provider-catalog)
                                             (vals provider-catalog)
                                             provider-catalog))))

(defn provider-candidates
  "Returns only active, merchant-owned provider accounts as M18 candidates.
  It does not choose a provider; the pure routing policy remains responsible for
  that decision."
  [runtime {:keys [merchant-id] :as merchant-context} provider-catalog]
  (when-not (and (string? merchant-id) (not (string/blank? merchant-id)))
    (resolution-error! :merchant-provider/missing-merchant-context))
  (let [catalog (catalog-by-provider provider-catalog)]
    (->> (repository/provider-configurations (:provider-configurations runtime) merchant-id)
         (filter :merchant-provider-configuration/enabled)
         (keep (fn [configuration]
                 (let [configured-account (get configuration :merchant-provider-configuration/provider-account)
                       account-id (:provider-account/id configured-account)
                       account (repository/find-provider-account (:provider-accounts runtime) merchant-id account-id)
                       provider (:provider-account/provider account)
                       descriptor (get catalog provider)
                       config (:merchant-provider-configuration/config configuration)
                       configured-methods (configured-method-capabilities (:payment-methods config))]
                   (when (and account descriptor
                              (= merchant-id (get-in account [:provider-account/merchant :merchant/id]))
                              (= :active (:provider-account/status account))
                              (not= false (:available? config))
                              (= provider (:provider-account/provider configured-account)))
                     (cond-> (merge descriptor {:provider provider
                                                 :merchant-provider/account-id account-id})
                       (:currencies config) (update :currencies #(if % (set/intersection % (:currencies config))
                                                                      (:currencies config)))
                       configured-methods (update :capabilities
                                                  #(if % (into (set (remove (fn [capability]
                                                                              (= "method" (namespace capability))) %))
                                                               configured-methods)
                                                       configured-methods)))))))
         vec)))

(defn create-gateway
  "Builds a gateway from one resolved merchant context. The factory is selected
  only by the explicitly requested provider; this is not a routing policy."
  [runtime merchant-context provider]
  (let [resolved (resolve-provider-runtime runtime merchant-context provider)
        factory (get (:gateway-factories runtime) provider)]
    (when-not factory (resolution-error! :merchant-provider/gateway-factory-not-found))
    (factory resolved)))
