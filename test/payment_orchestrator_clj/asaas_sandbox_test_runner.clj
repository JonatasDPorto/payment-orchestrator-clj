(ns payment-orchestrator-clj.asaas-sandbox-test-runner
  (:require [clojure.test :as test] [payment-orchestrator-clj.provider.asaas-sandbox-integration-test]))
(defn -main [& _] (let [{:keys [fail error]} (test/run-tests 'payment-orchestrator-clj.provider.asaas-sandbox-integration-test)] (when (pos? (+ fail error)) (System/exit 1))))
