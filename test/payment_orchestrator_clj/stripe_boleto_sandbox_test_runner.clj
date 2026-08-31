(ns payment-orchestrator-clj.stripe-boleto-sandbox-test-runner
  (:require [clojure.test :as test]
            [payment-orchestrator-clj.provider.stripe-boleto-sandbox-integration-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (test/run-tests 'payment-orchestrator-clj.provider.stripe-boleto-sandbox-integration-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
