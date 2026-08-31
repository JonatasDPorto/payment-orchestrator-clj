(ns payment-orchestrator-clj.subscription.domain)

(def subscription-statuses #{:subscription.status/active :subscription.status/cancelled})
(def invoice-statuses #{:invoice.status/open :invoice.status/paid :invoice.status/void})

(defn- invalid! [code data] (throw (ex-info (name code) (assoc data :error/code code))))

(defn new-subscription [{:keys [id merchant-id customer-id amount currency interval occurred-at]}]
  (when-not (uuid? id) (invalid! :subscription.validation/invalid-id {}))
  (when-not (and (string? customer-id) (seq customer-id)) (invalid! :subscription.validation/invalid-customer {}))
  (when-not (and (integer? amount) (pos? amount)) (invalid! :subscription.validation/invalid-amount {}))
  (when-not (= :BRL currency) (invalid! :subscription.validation/unsupported-currency {}))
  (when-not (= :month interval) (invalid! :subscription.validation/unsupported-interval {}))
  {:subscription/id id :subscription/merchant-id (or merchant-id "default")
   :subscription/customer-id customer-id :subscription/amount amount :subscription/currency currency
   :subscription/interval interval :subscription/status :subscription.status/active :subscription/created-at occurred-at})

(defn cancel [subscription]
  (if (= :subscription.status/active (:subscription/status subscription))
    (assoc subscription :subscription/status :subscription.status/cancelled)
    (invalid! :subscription.transition/not-allowed {:subscription/id (:subscription/id subscription)})))

(defn issue-invoice [{:keys [id subscription-id amount currency due-at occurred-at]}]
  (when-not (and (uuid? id) (uuid? subscription-id)) (invalid! :invoice.validation/invalid-id {}))
  (when-not (and (integer? amount) (pos? amount) (= :BRL currency)) (invalid! :invoice.validation/invalid-money {}))
  {:invoice/id id :invoice/subscription-id subscription-id :invoice/amount amount :invoice/currency currency
   :invoice/status :invoice.status/open :invoice/due-at due-at :invoice/created-at occurred-at})

(defn attach-payment [invoice payment-id]
  (when-not (uuid? payment-id) (invalid! :invoice.validation/invalid-payment {}))
  (assoc invoice :invoice/payment-id payment-id))
