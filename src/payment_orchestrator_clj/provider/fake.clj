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

(defn- pix-action []
  {:action/type :pix/qr-code
   :action/qr-code-url "https://fake-provider.test/pix/qr-code.svg"
   :action/hosted-instructions-url "https://fake-provider.test/pix/instructions"
   :action/payload "00020126580014br.gov.bcb.pix0136payment-orchestrator-clj-test5204000053039865802BR5925Payment Orchestrator Test6009Sao Paulo62070503***6304ABCD"
   :action/expires-at (java.time.Instant/parse "2030-01-01T00:00:00Z")})

(defrecord FakeGateway [mode payments latency-ms]
  port/PaymentGateway
  (capabilities [_] #{:payment/create :payment/fetch :payment/refund :payment/cancel :method/card :method/pix})
  (create-payment! [_ command]
    (if (= :payment.method/pix (:method command))
      (let [payment (result command :provider.status/requires-action "PIX_QR_CODE" (pix-action))]
        (swap! payments assoc (:provider-payment/reference payment) payment)
        payment)
      (case mode
      :always-success (let [payment (result command :provider.status/processing "PROCESSING")]
                        (swap! payments assoc (:provider-payment/reference payment) payment)
                        payment)
      :slow-success (do (Thread/sleep latency-ms)
                        (let [payment (result command :provider.status/processing "PROCESSING")]
                          (swap! payments assoc (:provider-payment/reference payment) payment)
                          payment))
      :requires-action (let [payment (result command :provider.status/requires-action "REQUIRES_ACTION"
                                             {:action/type :redirect :action/url "https://fake-provider.test/action"})]
                         (swap! payments assoc (:provider-payment/reference payment) payment)
                         payment)
      :always-fail (throw (port/provider-error :provider.error/declined
                                                {:provider :fake :retryable? false :outcome-known? true}))
      :timeout (throw (port/provider-error :provider.error/timeout
                                           {:provider :fake :provider-reference (reference (:payment/id command))
                                            :retryable? false :outcome-known? false}))
      :commit-then-timeout (let [payment (result command :provider.status/succeeded "SUCCEEDED")]
                             (swap! payments assoc (:provider-payment/reference payment) payment)
                             (throw (port/provider-error :provider.error/timeout
                                                        {:provider :fake :provider-reference (:provider-payment/reference payment)
                                                         :retryable? false :outcome-known? false})))
      :commit-then-timeout-fetch-unavailable (let [payment (result command :provider.status/succeeded "SUCCEEDED")]
                                                (swap! payments assoc (:provider-payment/reference payment) payment)
                                                (throw (port/provider-error :provider.error/timeout
                                                                           {:provider :fake :provider-reference (:provider-payment/reference payment)
                                                                            :retryable? false :outcome-known? false})))
      (throw (port/provider-error :provider.error/unexpected-response
                                  {:provider :fake :retryable? false :outcome-known? true})))))
  (fetch-payment [_ provider-reference]
    (if (= mode :commit-then-timeout-fetch-unavailable)
      (throw (port/provider-error :provider.error/unavailable
                                  {:provider :fake :retryable? true :outcome-known? false}))
      (or (get @payments provider-reference)
          (throw (port/provider-error :provider.error/invalid-request
                                      {:provider :fake :provider-reference provider-reference
                                       :retryable? false :outcome-known? true})))))
  (cancel-payment! [_ command] (result command :provider.status/cancelled "CANCELLED"))
  (refund-payment! [_ command] (result command :provider.status/succeeded "REFUNDED")))

(defn new-gateway [{:keys [mode latency-ms] :or {mode :always-success latency-ms 5000}}]
  (->FakeGateway mode (atom {}) latency-ms))
