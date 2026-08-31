(ns payment-orchestrator-clj.subscription.datomic-repository-integration-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.subscription.datomic-repository :as datomic-repository]
            [payment-orchestrator-clj.subscription.repository :as repository]
            [payment-orchestrator-clj.subscription.domain :as domain])
  (:import [java.time Instant]))

(deftest subscription-and-invoice-have-independent-persistence-lifecycles
  (support/with-test-database
   (fn [connection]
     (let [repo (datomic-repository/new-repository connection)
           now (Instant/parse "2026-08-31T12:00:00Z")
           subscription (domain/new-subscription {:id #uuid "11111111-1111-1111-1111-111111111111" :customer-id "customer-123" :amount 12990 :currency :BRL :interval :month :occurred-at now})
           invoice (domain/issue-invoice {:id #uuid "22222222-2222-2222-2222-222222222222" :subscription-id (:subscription/id subscription) :amount 12990 :currency :BRL :due-at now :occurred-at now})]
       (repository/save-subscription! repo subscription)
       (repository/save-invoice! repo invoice)
       (is (= subscription (repository/find-subscription repo (:subscription/id subscription))))
       (is (= invoice (repository/find-invoice repo (:invoice/id invoice))))))))
