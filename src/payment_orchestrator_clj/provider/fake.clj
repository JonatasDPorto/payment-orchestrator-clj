(ns payment-orchestrator-clj.provider.fake
  "Deterministic provider adapter used to prove the canonical gateway boundary."
  (:require [payment-orchestrator-clj.provider.port :as port]))

(defn- reference [payment-id] (str "fake-" payment-id))

(defn- result [command status raw-status & [action]]
  (cond-> {:provider :fake
           :provider-payment/reference (reference (:payment/id command))
           :provider-payment/status status
           :provider-payment/raw-status raw-status}
    action (assoc :provider-payment/action action)))

(defrecord FakeGateway [mode payments]
  port/PaymentGateway
  (capabilities [_] #{:payment/create :payment/fetch :payment/refund :payment/cancel :method/card})
  (create-payment! [_ command]
    (case mode
      :always-success (let [payment (result command :provider.status/processing "PROCESSING")]
                        (swap! payments assoc (:provider-payment/reference payment) payment)
                        payment)
      :requires-action (let [payment (result command :provider.status/requires-action "REQUIRES_ACTION"
                                             {:action/type :redirect :action/url "https://fake-provider.test/action"})]
                         (swap! payments assoc (:provider-payment/reference payment) payment)
                         payment)
      :always-fail (throw (port/provider-error :provider.error/declined
                                                {:provider :fake :retryable? false :outcome-known? true}))
      :timeout (throw (port/provider-error :provider.error/timeout
                                           {:provider :fake :retryable? false :outcome-known? false}))
      (throw (port/provider-error :provider.error/unexpected-response
                                  {:provider :fake :retryable? false :outcome-known? true}))))
  (fetch-payment [_ provider-reference]
    (or (get @payments provider-reference)
        (throw (port/provider-error :provider.error/invalid-request
                                    {:provider :fake :provider-reference provider-reference
                                     :retryable? false :outcome-known? true}))))
  (cancel-payment! [_ command] (result command :provider.status/cancelled "CANCELLED"))
  (refund-payment! [_ command] (result command :provider.status/succeeded "REFUNDED")))

(defn new-gateway [{:keys [mode] :or {mode :always-success}}]
  (->FakeGateway mode (atom {})))
