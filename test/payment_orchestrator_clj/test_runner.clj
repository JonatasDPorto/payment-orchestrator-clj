(ns payment-orchestrator-clj.test-runner
  (:require [clojure.test :as test]
            [payment-orchestrator-clj.api.payment-test]
            [payment-orchestrator-clj.config-test]
            [payment-orchestrator-clj.event-consumer-test]
            [payment-orchestrator-clj.core-test]
            [payment-orchestrator-clj.logging-test]
            [payment-orchestrator-clj.ledger.domain-test]
            [payment-orchestrator-clj.payment.domain-test]
            [payment-orchestrator-clj.provider.contract-test]
            [payment-orchestrator-clj.provider.routing-test]
            [payment-orchestrator-clj.provider.stripe-adapter-test]
            [payment-orchestrator-clj.provider.stripe-errors-test]
            [payment-orchestrator-clj.provider.stripe-mapper-test]
            [payment-orchestrator-clj.performance-test]
            [payment-orchestrator-clj.security-test]
            [payment-orchestrator-clj.subscription.domain-test]
            [payment-orchestrator-clj.webhook-test]))

(def test-namespaces
  ['payment-orchestrator-clj.api.payment-test
   'payment-orchestrator-clj.config-test
   'payment-orchestrator-clj.event-consumer-test
   'payment-orchestrator-clj.core-test
   'payment-orchestrator-clj.logging-test
   'payment-orchestrator-clj.ledger.domain-test
   'payment-orchestrator-clj.payment.domain-test
   'payment-orchestrator-clj.provider.contract-test
   'payment-orchestrator-clj.provider.routing-test
   'payment-orchestrator-clj.provider.stripe-adapter-test
   'payment-orchestrator-clj.provider.stripe-errors-test
   'payment-orchestrator-clj.provider.stripe-mapper-test
   'payment-orchestrator-clj.performance-test
   'payment-orchestrator-clj.security-test
   'payment-orchestrator-clj.subscription.domain-test
   'payment-orchestrator-clj.webhook-test])

(defn -main [& _]
  (let [{:keys [fail error]} (apply test/run-tests test-namespaces)]
    (when (pos? (+ fail error))
      (System/exit 1))))
