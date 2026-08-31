(ns payment-orchestrator-clj.ledger.service
  (:require [payment-orchestrator-clj.ledger.domain :as domain]
            [payment-orchestrator-clj.ledger.repository :as repository]))

(defn record-payment-settlement!
  "Records the sole M9 settlement journal. Safe to call after retries/replays."
  [{:keys [ledger clock id-generator]} payment context]
  (when (and ledger (= :payment.status/paid (:payment/status payment)))
    (repository/record-journal! ledger
                                (domain/payment-settled-journal payment id-generator (clock))
                                context)))
