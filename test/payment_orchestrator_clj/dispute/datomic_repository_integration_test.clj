(ns payment-orchestrator-clj.dispute.datomic-repository-integration-test
  (:require [clojure.test :refer [deftest is]] [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.dispute.domain :as domain] [payment-orchestrator-clj.dispute.datomic-repository :as datomic]
            [payment-orchestrator-clj.dispute.repository :as repository])
  (:import [java.time Instant] [java.util UUID]))
(deftest dispute-round-trips-and-has-provider-deduplication
  (support/with-test-database (fn [connection]
    (let [repo (datomic/new-repository connection) dispute (domain/new-dispute {:id (UUID/randomUUID) :payment-id (UUID/randomUUID) :amount 100 :currency :BRL :reason "fraudulent" :provider :stripe :provider-reference "dp_123" :occurred-at (Instant/parse "2026-08-31T00:00:00Z")})]
      (repository/save-dispute! repo dispute {:source :source/test})
      (is (= dispute (repository/find-dispute repo (:dispute/id dispute))))
      (is (= dispute (repository/find-dispute-by-provider-reference repo :stripe "dp_123")))))))
