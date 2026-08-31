(ns payment-orchestrator-clj.integration-test-runner
  (:require [clojure.test :as test]
            [payment-orchestrator-clj.payment.datomic-repository-integration-test]
            [payment-orchestrator-clj.payment.idempotency-integration-test]
            [payment-orchestrator-clj.payment.provider-integration-test]
            [payment-orchestrator-clj.ledger-integration-test]
            [payment-orchestrator-clj.webhook-integration-test]))

(def test-namespaces
  ['payment-orchestrator-clj.payment.datomic-repository-integration-test
   'payment-orchestrator-clj.payment.idempotency-integration-test
   'payment-orchestrator-clj.payment.provider-integration-test
   'payment-orchestrator-clj.ledger-integration-test
   'payment-orchestrator-clj.webhook-integration-test])

(defn -main [& _]
  (let [{:keys [fail error]} (apply test/run-tests test-namespaces)]
    (when (pos? (+ fail error))
      (System/exit 1))))
