(ns payment-orchestrator-clj.ledger.repository)

(defprotocol LedgerRepository
  (ensure-accounts! [repository])
  (record-journal! [repository journal transaction-context])
  (payment-journals [repository payment-id]))
