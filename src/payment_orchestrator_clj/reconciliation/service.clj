(ns payment-orchestrator-clj.reconciliation.service
  (:refer-clojure :exclude [run!])
  (:require [payment-orchestrator-clj.ledger.service :as ledger]
            [payment-orchestrator-clj.payment.domain :as domain]
            [payment-orchestrator-clj.payment.repository :as payments]
            [payment-orchestrator-clj.provider.port :as provider]
            [payment-orchestrator-clj.observability.metrics :as metrics]
            [payment-orchestrator-clj.reconciliation.repository :as operations]))

(defn- provider->payment-status [result]
  (case (:provider-payment/status result)
    :provider.status/succeeded :payment.status/paid
    :provider.status/failed :payment.status/failed
    :provider.status/cancelled :payment.status/cancelled
    :provider.status/requires-action :payment.status/requires-action
    :provider.status/processing :payment.status/processing))

(defn- context [operation]
  {:source :source/reconciliation :actor :actor/reconciliation-worker
   :reason :reason/provider-outcome-unknown :event-type :event/payment-reconciled
   :request-id (str (:provider-operation/id operation))
   :correlation-id (str (:provider-operation/id operation))})

(defn- reconcile! [{:keys [gateway payments ledger clock id-generator operations] :as dependencies} operation]
  (let [payment (:provider-operation/payment operation)
        now (clock)
        tx-context (context operation)
        base {:reconciliation/id (id-generator) :reconciliation/payment (:payment/id payment)
              :reconciliation/provider (:provider-operation/provider operation)
              :reconciliation/reason :reason/provider-outcome-unknown
              :reconciliation/local-status (:payment/status payment) :reconciliation/created-at now}]
    (try
      (let [remote (provider/fetch-payment gateway (:provider-operation/provider-reference operation))
            target (provider->payment-status remote)
            changed (if (= target (:payment/status payment)) payment (domain/transition payment target now))
            result (if (= changed payment) :reconciliation.result/matched :reconciliation.result/corrected)]
        (when-not (= changed payment) (payments/save-payment! payments changed tx-context))
        (ledger/record-payment-settlement! dependencies changed tx-context)
        (operations/complete-operation! operations (:provider-operation/id operation)
                                      {:provider-operation/status :provider-operation.status/reconciled
                                       :provider-operation/provider (:provider remote)
                                       :provider-operation/provider-reference (:provider-payment/reference remote)
                                       :provider-operation/completed-at now} tx-context)
        (operations/record-reconciliation! operations (assoc base :reconciliation/remote-status target :reconciliation/result result) tx-context)
        (when-let [registry (:metrics dependencies)]
          (metrics/inc! registry "reconciliation_total")
          (when (= :reconciliation.result/corrected result)
            (metrics/inc! registry "reconciliation_mismatch_total")))
        result)
      (catch clojure.lang.ExceptionInfo error
        (let [data (ex-data error)]
          (when-not (:retryable? data)
            (operations/record-reconciliation! operations
                                                (assoc base :reconciliation/result :reconciliation.result/manual-review)
                                                tx-context))
          :reconciliation.result/deferred)))))

(defn run! [dependencies]
  (mapv #(reconcile! dependencies %) (operations/unresolved-operations (:operations dependencies))))
