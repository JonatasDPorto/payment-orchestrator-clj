(ns payment-orchestrator-clj.asaas-sandbox-cleanup-runner
  (:require [payment-orchestrator-clj.provider.asaas-sandbox-integration-test :as sandbox]))
(defn -main [& _] (println "ASAAS_SANDBOX_CLEANUP" (sandbox/cleanup-sandbox-payments!)))
