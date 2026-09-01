(ns payment-orchestrator-clj.dispute.repository)
(defprotocol DisputeRepository
  (save-dispute! [repository dispute transaction-context])
  (find-dispute [repository dispute-id])
  (find-dispute-by-provider-reference [repository provider provider-reference]))
