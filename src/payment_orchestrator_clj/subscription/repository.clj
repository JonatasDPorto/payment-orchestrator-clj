(ns payment-orchestrator-clj.subscription.repository)

(defprotocol SubscriptionRepository
  (save-subscription! [repository subscription])
  (find-subscription [repository subscription-id])
  (save-invoice! [repository invoice])
  (find-invoice [repository invoice-id]))
