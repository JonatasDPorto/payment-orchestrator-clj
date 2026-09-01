(ns payment-orchestrator-clj.asaas-sandbox-audit-runner
  (:require [payment-orchestrator-clj.provider.asaas-sandbox-integration-test :as sandbox]))
(defn -main [& _] (println "ASAAS_SANDBOX_AUDIT" (sandbox/audit-sandbox-payments!)))
