(ns payment-orchestrator-clj.provider.routing
  "Pure provider-selection policy. Gateways are opaque values in provider descriptors."
  (:require [payment-orchestrator-clj.provider.port :as port]))

(defn- method-capability [method]
  (keyword "method" (name method)))

(defn- provider-id [provider]
  (or (:provider provider) (:id provider)))

(defn- normalized-providers [providers]
  (let [values (if (map? providers) (vals providers) providers)]
    (->> values
         (map #(assoc % :provider (provider-id %)))
         (sort-by (comp str :provider)))))

(defn- supports-context? [provider {:keys [method currency]}]
  (let [capabilities (:capabilities provider #{})]
    (and (not= false (:available? provider))
         (or (nil? method) (contains? capabilities (method-capability method)))
         (or (nil? currency) (not (contains? provider :currencies))
             (contains? (:currencies provider) currency)))))

(defn- configured-provider [context]
  (let [{:keys [merchant-id currency method routing]} context]
    (or (get-in routing [:by-merchant merchant-id])
        (get-in routing [:by-currency currency])
        (get-in routing [:by-payment-method method])
        (:default-provider routing))))

(defn- unavailable! [context requested]
  (throw (port/provider-error :provider.error/unavailable
                              {:provider requested
                               :retryable? true
                               :outcome-known? true
                               :routing/error :routing/no-available-provider
                               :routing/context (select-keys context [:merchant-id :currency :method])})))

(defn select-provider
  "Selects one provider descriptor from a pure routing context.

  `context` contains `:merchant-id`, `:currency`, `:method`, and `:routing`.
  Routes have precedence merchant -> currency -> payment method -> default. When
  `:routing/strategy` is `:routing.strategy/lowest-cost`, the available compatible
  provider with the smallest numeric `:cost` is selected instead. A configured
  provider that is unavailable is rejected; this function never silently falls
  back to another provider, which is essential for unknown payment outcomes."
  [context providers]
  (let [candidates (filter #(supports-context? % context) (normalized-providers providers))
        requested (configured-provider context)]
    (cond
      (= :routing.strategy/lowest-cost (get-in context [:routing :strategy]))
      (or (first (sort-by (juxt #(or (:cost %) Long/MAX_VALUE) (comp str :provider)) candidates))
          (unavailable! context nil))

      requested
      (or (some #(when (= requested (:provider %)) %) candidates)
          (unavailable! context requested))

      :else
      (unavailable! context nil))))
