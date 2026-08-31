(ns payment-orchestrator-clj.payment.domain
  "Pure canonical payment domain. Infrastructure belongs in later milestones.")

(def supported-currencies #{:BRL})
(def supported-payment-methods #{:payment.method/card})

(def payment-statuses
  #{:payment.status/created :payment.status/processing :payment.status/requires-action
    :payment.status/authorized :payment.status/paid :payment.status/failed
    :payment.status/cancelled :payment.status/partially-refunded :payment.status/refunded})

(def allowed-transitions
  {:payment.status/created #{:payment.status/processing :payment.status/cancelled}
   :payment.status/processing #{:payment.status/requires-action :payment.status/authorized
                                :payment.status/paid :payment.status/failed :payment.status/cancelled}
   :payment.status/requires-action #{:payment.status/processing :payment.status/failed :payment.status/cancelled}
   :payment.status/authorized #{:payment.status/paid :payment.status/cancelled}
   :payment.status/paid #{:payment.status/partially-refunded :payment.status/refunded}
   :payment.status/partially-refunded #{:payment.status/partially-refunded :payment.status/refunded}
   :payment.status/failed #{}
   :payment.status/cancelled #{}
   :payment.status/refunded #{}})

(defn- invalid! [code data]
  (throw (ex-info (name code) (assoc data :error/code code))))

(defn money
  "Creates canonical money using the currency's minor unit as an integer."
  [{:keys [amount currency]}]
  (when-not (integer? amount)
    (invalid! :payment.validation/amount-must-be-integer {:amount amount}))
  (when-not (pos? amount)
    (invalid! :payment.validation/amount-must-be-positive {:amount amount}))
  (when-not (contains? supported-currencies currency)
    (invalid! :payment.validation/unsupported-currency {:currency currency}))
  {:money/amount amount :money/currency currency})

(defn- validate-payment-command! [{:keys [id customer-id method]}]
  (when-not (uuid? id)
    (invalid! :payment.validation/invalid-id {:payment/id id}))
  (when-not (and (string? customer-id) (seq customer-id))
    (invalid! :payment.validation/invalid-customer-id {:payment/customer-id customer-id}))
  (when-not (contains? supported-payment-methods method)
    (invalid! :payment.validation/unsupported-payment-method {:payment/method method})))

(defn- event [type payment-id occurred-at]
  {:event/type type :event/payment-id payment-id :event/occurred-at occurred-at})

(defn new-payment
  "Creates a canonical payment. :occurred-at records an event and is caller-supplied for determinism."
  [{:keys [id customer-id amount currency method occurred-at] :as command}]
  (validate-payment-command! command)
  (let [{:money/keys [amount currency]} (money {:amount amount :currency currency})]
    (cond-> {:payment/id id :payment/customer-id customer-id :payment/amount amount
             :payment/currency currency :payment/method method
             :payment/status :payment.status/created :payment/events []}
      occurred-at (assoc :payment/created-at occurred-at)
      occurred-at (update :payment/events conj (event :payment/created id occurred-at)))))

(defn transition-allowed? [payment to-status]
  (contains? (get allowed-transitions (:payment/status payment) #{}) to-status))

(defn transition
  "Moves a payment through the state machine. An optional timestamp records a domain event."
  ([payment to-status] (transition payment to-status nil))
  ([payment to-status occurred-at]
   (when-not (contains? payment-statuses to-status)
     (invalid! :payment.transition/unknown-status {:to-status to-status}))
   (when-not (transition-allowed? payment to-status)
     (invalid! :payment.transition/not-allowed
               {:payment/id (:payment/id payment) :from-status (:payment/status payment) :to-status to-status}))
   (cond-> (assoc payment :payment/status to-status)
     occurred-at (update :payment/events conj
                         (event (keyword "payment" (name to-status)) (:payment/id payment) occurred-at)))))
