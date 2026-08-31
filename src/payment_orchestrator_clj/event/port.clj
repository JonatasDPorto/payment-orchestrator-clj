(ns payment-orchestrator-clj.event.port)

(defprotocol EventProducer
  (publish! [producer event]))
