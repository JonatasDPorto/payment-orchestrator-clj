(ns payment-orchestrator-clj.payment.repository)

(defprotocol PaymentRepository
  (save-payment! [repository payment]
    [repository payment transaction-context])
  (create-payment-idempotently! [repository payment idempotency-record transaction-context])
  (record-provider-result! [repository payment provider-result transaction-context])
  (find-payment [repository payment-id]))
