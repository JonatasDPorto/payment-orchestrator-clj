(ns payment-orchestrator-clj.test-runner
  (:require [clojure.test :as test]
            [payment-orchestrator-clj.api.payment-test]
            [payment-orchestrator-clj.config-test]
            [payment-orchestrator-clj.core-test]
            [payment-orchestrator-clj.logging-test]
            [payment-orchestrator-clj.payment.domain-test]
            [payment-orchestrator-clj.provider.contract-test]))

(def test-namespaces
  ['payment-orchestrator-clj.api.payment-test
   'payment-orchestrator-clj.config-test
   'payment-orchestrator-clj.core-test
   'payment-orchestrator-clj.logging-test
   'payment-orchestrator-clj.payment.domain-test
   'payment-orchestrator-clj.provider.contract-test])

(defn -main [& _]
  (let [{:keys [fail error]} (apply test/run-tests test-namespaces)]
    (when (pos? (+ fail error))
      (System/exit 1))))
