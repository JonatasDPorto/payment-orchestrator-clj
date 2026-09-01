(ns payment-orchestrator-clj.ledger.service
  (:require [payment-orchestrator-clj.ledger.domain :as domain]
            [payment-orchestrator-clj.observability.metrics :as metrics]
            [payment-orchestrator-clj.ledger.repository :as repository]))

(defn record-payment-settlement!
  "Records the sole M9 settlement journal. Safe to call after retries/replays."
  [{:keys [ledger clock id-generator metrics]} payment context]
  (when (and ledger (= :payment.status/paid (:payment/status payment)))
    (try
      (repository/record-journal! ledger
                                  (domain/payment-settled-journal payment id-generator (clock))
                                  context)
      (catch clojure.lang.ExceptionInfo error
        (when (and metrics (:ledger/invalid? (ex-data error)))
          (metrics/inc! metrics "ledger_invariant_failure_total"))
        (throw error)))))
