(ns payment-orchestrator-clj.reconciliation.repository)

(defprotocol ReconciliationRepository
  (start-operation! [repository operation context])
  (complete-operation! [repository operation-id result context])
  (unresolved-operations [repository])
  (record-reconciliation! [repository reconciliation context]))
