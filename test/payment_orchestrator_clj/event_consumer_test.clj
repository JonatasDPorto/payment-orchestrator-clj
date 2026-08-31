(ns payment-orchestrator-clj.event-consumer-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.event.consumer :as consumer]))

(deftest duplicate-event-id-has-one-effective-consumer-effect
  (let [effects (atom [])
        handler (consumer/deduplicating-handler #(swap! effects conj (:event/id %)))
        event {:event/id "deterministic-event" :event/type "payment.paid"}]
    (is (= :handled (:outcome (consumer/handle! handler event))))
    (is (= :duplicate (:outcome (consumer/handle! handler event))))
    (is (= ["deterministic-event"] @effects))))
