(ns payment-orchestrator-clj.refund.repository)

(defprotocol RefundRepository
  (save-refund! [repository refund transaction-context])
  (find-refund [repository refund-id])
  (refunds-for-payment [repository payment-id])
  (provider-payment-for [repository payment-id])
  (record-reconciliation! [repository reconciliation transaction-context]))
