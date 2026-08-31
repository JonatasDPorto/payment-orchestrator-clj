(ns payment-orchestrator-clj.refund.domain)

(defn- invalid! [code data] (throw (ex-info (name code) (assoc data :error/code code))))
(defn refunded-amount [refunds] (reduce + 0 (map :refund/amount refunds)))
(defn remaining-amount [captured refunds] (- captured (refunded-amount refunds)))
(defn new-refund [{:keys [id payment-id amount captured-amount existing-refunds occurred-at]}]
  (when-not (and (uuid? id) (uuid? payment-id) (integer? amount) (pos? amount))
    (invalid! :refund.validation/invalid-refund {}))
  (when (> (+ amount (refunded-amount existing-refunds)) captured-amount)
    (invalid! :refund.validation/exceeds-captured {:captured-amount captured-amount :requested-amount amount}))
  {:refund/id id :refund/payment-id payment-id :refund/amount amount :refund/status :refund.status/succeeded :refund/created-at occurred-at})

(defn payment-status-after-refund [captured refunds]
  (if (= captured (refunded-amount refunds)) :payment.status/refunded :payment.status/partially-refunded))

(defn reconcile [captured local-refunds remote-refunds]
  (let [local (refunded-amount local-refunds) remote (refunded-amount remote-refunds)]
    {:refund-reconciliation/result (if (= local remote) :matched :mismatch)
     :refund-reconciliation/local-amount local :refund-reconciliation/remote-amount remote
     :refund-reconciliation/captured-amount captured}))
