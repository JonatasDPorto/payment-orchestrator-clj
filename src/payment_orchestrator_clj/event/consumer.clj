(ns payment-orchestrator-clj.event.consumer
  "Small idempotent consumer boundary used by downstream consumers in demos/tests."
  (:require [payment-orchestrator-clj.event.port :as producer]))

(defprotocol EventHandler
  (handle! [handler event]))

(defrecord DeduplicatingHandler [seen delegate]
  EventHandler
  (handle! [_ event]
    (if (contains? @seen (:event/id event))
      {:outcome :duplicate}
      (do (swap! seen conj (:event/id event))
          (when delegate (delegate event))
          {:outcome :handled}))))

(defn deduplicating-handler [delegate]
  (->DeduplicatingHandler (atom #{}) delegate))
