(ns payment-orchestrator-clj.audit.repository)

(defprotocol PaymentAuditRepository
  (payment-as-of [repository payment-id point])
  (payment-history [repository payment-id]))
