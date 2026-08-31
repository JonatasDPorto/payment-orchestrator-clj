(ns payment-orchestrator-clj.payment.service
  "Application orchestration for payment commands."
  (:require [payment-orchestrator-clj.payment.domain :as domain]
            [payment-orchestrator-clj.payment.idempotency :as idempotency]
            [payment-orchestrator-clj.payment.repository :as repository]
            [payment-orchestrator-clj.ledger.service :as ledger]
            [payment-orchestrator-clj.provider.port :as provider]))

(defn create-payment!
  "Creates and persists a payment using injected time and ID generators."
  ([dependencies command]
   (create-payment! dependencies command {:source :source/application}))
  ([{:keys [payments clock id-generator]} command transaction-context]
   (let [payment (domain/new-payment (assoc command :id (id-generator) :occurred-at (clock)))]
     (repository/save-payment! payments payment transaction-context)
     payment)))

(defn- to-processing [payment occurred-at]
  (if (= :payment.status/created (:payment/status payment))
    (domain/transition payment :payment.status/processing occurred-at)
    payment))

(defn- apply-provider-result [payment provider-result occurred-at]
  (case (:provider-payment/status provider-result)
    :provider.status/processing (to-processing payment occurred-at)
    :provider.status/requires-action (domain/transition (to-processing payment occurred-at)
                                                        :payment.status/requires-action occurred-at)
    :provider.status/succeeded (domain/transition (to-processing payment occurred-at)
                                                  :payment.status/paid occurred-at)
    :provider.status/failed (domain/transition (to-processing payment occurred-at)
                                               :payment.status/failed occurred-at)
    :provider.status/cancelled (domain/transition (to-processing payment occurred-at)
                                                  :payment.status/cancelled occurred-at)
    (throw (provider/provider-error :provider.error/unexpected-response
                                    {:provider (:provider provider-result)
                                     :retryable? false :outcome-known? true}))))

(defn- provider-command [payment idempotency-key]
  {:operation/id (java.util.UUID/randomUUID)
   :payment/id (:payment/id payment)
   :amount (:payment/amount payment)
   :currency (:payment/currency payment)
   :method (:payment/method payment)
   :customer {:reference (:payment/customer-id payment)}
   :idempotency-key idempotency-key})

(defn create-payment-idempotently!
  "Creates a payment once per consumer key and returns :created, :replayed or :conflict."
  [{:keys [payments gateway clock id-generator] :as dependencies} command idempotency-key transaction-context]
  (let [now (clock)
        payment (domain/new-payment (assoc command :id (id-generator) :occurred-at now))
        record {:idempotency/key idempotency-key
                :idempotency/request-hash (idempotency/request-hash command)
                :idempotency/payment-id (:payment/id payment)
                :idempotency/created-at now}
        local-result (repository/create-payment-idempotently! payments payment record transaction-context)]
    (if (not= :created (:outcome local-result))
      local-result
      (try
        (let [raw-provider-result (provider/create-payment! gateway (provider-command payment idempotency-key))
              provider-result (assoc raw-provider-result :provider-payment/created-at now)
              updated (apply-provider-result payment provider-result now)]
          (let [stored (repository/record-provider-result! payments updated provider-result transaction-context)]
            (ledger/record-payment-settlement! {:ledger (:ledger dependencies) :clock clock :id-generator id-generator}
                                               stored transaction-context)
            {:outcome :created :payment stored :provider-result provider-result}))
        (catch clojure.lang.ExceptionInfo error
          (if (and (:provider/error? (ex-data error))
                   (:outcome-known? (ex-data error)))
            (let [failed (domain/transition (to-processing payment now) :payment.status/failed now)]
              (repository/save-payment! payments failed transaction-context))
            nil)
          (throw error))))))
