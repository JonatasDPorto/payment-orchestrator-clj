(ns payment-orchestrator-clj.dispute.domain-test
  (:require [clojure.test :refer [deftest is]] [payment-orchestrator-clj.dispute.domain :as domain])
  (:import [java.time Instant] [java.util UUID]))
(def dispute (domain/new-dispute {:id (UUID/randomUUID) :payment-id (UUID/randomUUID) :amount 100 :currency :BRL :reason "fraudulent" :provider :stripe :provider-reference "dp_123" :occurred-at (Instant/parse "2026-08-31T00:00:00Z")}))
(deftest dispute-is-a-separate-state-machine
  (is (= :dispute.status/needs-response (:dispute/status dispute)))
  (is (= :dispute.status/won (:dispute/status (domain/transition (domain/transition dispute :dispute.status/under-review) :dispute.status/won))))
  (is (thrown? clojure.lang.ExceptionInfo (domain/transition dispute :dispute.status/needs-response))))
