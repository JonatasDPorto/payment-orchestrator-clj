(ns payment-orchestrator-clj.provider.stripe.errors
  "Translation of Stripe transport failures to the canonical provider taxonomy."
  (:require [payment-orchestrator-clj.provider.port :as port]))

(defn- category-for-status [status]
  (cond
    (#{400 404} status) :provider.error/invalid-request
    (#{401 403} status) :provider.error/authentication
    (= 402 status) :provider.error/declined
    (= 429 status) :provider.error/rate-limited
    (<= 500 status 599) :provider.error/unavailable
    :else :provider.error/unexpected-response))

(defn response-error [{:keys [status body request-id]}]
  (let [category (category-for-status status)
        outcome-known? (not (<= 500 status 599))]
    (port/provider-error category
                         {:provider :stripe
                          :provider-code (or (get-in body [:error :code])
                                             (get-in body [:error :type]))
                          :provider-http-status status
                          :provider-request-id request-id
                          :retryable? (contains? #{:provider.error/rate-limited :provider.error/unavailable} category)
                          :outcome-known? outcome-known?})))

(defn timeout-error []
  (port/provider-error :provider.error/timeout
                       {:provider :stripe :retryable? false :outcome-known? false}))

(defn transport-error []
  (port/provider-error :provider.error/outcome-unknown
                       {:provider :stripe :retryable? false :outcome-known? false}))
