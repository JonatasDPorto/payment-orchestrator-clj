(ns payment-orchestrator-clj.stripe-pix-sandbox-test-runner
  (:require [clojure.test :as test]
            [payment-orchestrator-clj.provider.stripe-pix-sandbox-integration-test]))

(defn -main [& _]
  (let [{:keys [fail error]} (test/run-tests 'payment-orchestrator-clj.provider.stripe-pix-sandbox-integration-test)]
    (when (pos? (+ fail error))
      (System/exit 1))))
