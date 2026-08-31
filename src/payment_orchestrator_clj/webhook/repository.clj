(ns payment-orchestrator-clj.webhook.repository)

(defprotocol ProviderEventRepository
  (enqueue! [repository event transaction-context])
  (pending-events [repository])
  (payment-by-provider-reference [repository provider reference])
  (mark-processed! [repository event-id payment-id transaction-context])
  (mark-ignored! [repository event-id transaction-context])
  (record-processing-error! [repository event-id error-code transaction-context]))
