(ns payment-orchestrator-clj.refund.service
  "Application service for immutable, provider-agnostic refunds."
  (:require [payment-orchestrator-clj.payment.domain :as payment]
            [payment-orchestrator-clj.payment.repository :as payments]
            [payment-orchestrator-clj.provider.port :as provider]
            [payment-orchestrator-clj.provider.routing :as routing]
            [payment-orchestrator-clj.refund.domain :as domain]
            [payment-orchestrator-clj.refund.repository :as refunds]))

(defn- gateway-for [{:keys [gateway providers routing]} payment]
  (if (seq providers)
    (routing/select-provider {:merchant-id (:payment/merchant-id payment)
                              :currency (:payment/currency payment)
                              :method (:payment/method payment)
                              :routing routing}
                             providers)
    {:provider :provider/unknown :gateway gateway}))

(defn- refundable? [payment]
  (contains? #{:payment.status/paid :payment.status/partially-refunded}
             (:payment/status payment)))

(defn refund-payment!
  "Refunds a captured payment. The complete local history is checked before the
  provider call, so an amount above the remaining captured amount is rejected
  without issuing an external financial operation."
  [{:keys [payments refunds clock id-generator] :as dependencies} payment-id amount context]
  (locking payments
    (let [payment (payments/find-payment payments payment-id)]
      (when-not payment
        (throw (ex-info "payment-not-found" {:error/code :refund/payment-not-found :payment/id payment-id})))
      (when-not (refundable? payment)
        (throw (ex-info "payment-not-refundable"
                        {:error/code :refund/payment-not-refundable
                         :payment/id payment-id :payment/status (:payment/status payment)})))
      (if-let [previous (and (:refund/id context) (refunds/find-refund refunds (:refund/id context)))]
        {:refund previous :payment payment :outcome :replayed}
      (let [existing (refunds/refunds-for-payment refunds payment-id)
            now (clock)
            refund (domain/new-refund {:id (or (:refund/id context) (id-generator)) :payment-id payment-id :amount amount
                                       :captured-amount (:payment/amount payment)
                                       :existing-refunds existing :occurred-at now})
            provider-payment (refunds/provider-payment-for refunds payment-id)
            {:keys [gateway]} (gateway-for dependencies payment)]
        (when-not (and provider-payment (:provider-payment/reference provider-payment))
          (throw (ex-info "provider-payment-not-found"
                          {:error/code :refund/provider-payment-not-found :payment/id payment-id})))
        (let [provider-result (provider/refund-payment!
                               gateway {:operation/id (:refund/id refund)
                                        :payment/id payment-id :refund/amount amount
                                        :provider-payment/reference (:provider-payment/reference provider-payment)})]
          (when-not (= :provider.status/succeeded (:provider-payment/status provider-result))
            (throw (provider/provider-error :provider.error/unexpected-response
                                            {:provider (:provider provider-result)
                                             :retryable? false :outcome-known? true})))
          (let [stored (assoc refund :refund/provider (:provider provider-result)
                               :refund/provider-reference (:provider-refund/reference provider-result))
                all-refunds (conj existing stored)
                updated-payment (payment/transition payment
                                                    (domain/payment-status-after-refund (:payment/amount payment) all-refunds)
                                                    now)]
            (refunds/save-refund! refunds stored (assoc context :event-type :event/refund-created))
            (payments/save-payment! payments updated-payment (assoc context :event-type :event/payment-refunded))
            {:refund stored :payment updated-payment :outcome :created})))))))

(defn reconcile-payment-refunds!
  "Records a reconciliation snapshot supplied by a provider-specific worker.
  It deliberately does not mutate refunds: any mismatch requires investigation."
  [{:keys [refunds clock id-generator]} payment-id captured-amount remote-refunds context]
  (let [result (domain/reconcile captured-amount (refunds/refunds-for-payment refunds payment-id) remote-refunds)
        reconciliation {:refund-reconciliation/id (id-generator)
                        :refund-reconciliation/payment-id payment-id
                        :refund-reconciliation/local-amount (:refund-reconciliation/local-amount result)
                        :refund-reconciliation/remote-amount (:refund-reconciliation/remote-amount result)
                        :refund-reconciliation/result (:refund-reconciliation/result result)
                        :refund-reconciliation/created-at (clock)}]
    (refunds/record-reconciliation! refunds reconciliation (assoc context :event-type :event/refund-reconciled))
    result))
