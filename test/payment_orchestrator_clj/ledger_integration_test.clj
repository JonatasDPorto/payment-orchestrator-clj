(ns payment-orchestrator-clj.ledger-integration-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.ledger.datomic-repository :as datomic]
            [payment-orchestrator-clj.ledger.domain :as domain]
            [payment-orchestrator-clj.ledger.repository :as ledger]
            [payment-orchestrator-clj.payment.datomic-repository :as payments]
            [payment-orchestrator-clj.payment.domain :as payment-domain]
            [payment-orchestrator-clj.payment.repository :as payment-repository])
  (:import [java.time Instant] [java.util UUID]))

(deftest payment-settlement-is-immutable-and-idempotent
  (support/with-test-database
   (fn [connection]
     (let [payment-id (UUID/randomUUID)
           payment (payment-domain/transition
                    (payment-domain/new-payment {:id payment-id :customer-id "customer-123" :amount 10000
                                                 :currency :BRL :method :payment.method/card
                                                 :occurred-at (Instant/parse "2026-08-31T12:00:00Z")})
                    :payment.status/processing (Instant/parse "2026-08-31T12:00:00Z"))
           payment (payment-domain/transition payment :payment.status/paid (Instant/parse "2026-08-31T12:01:00Z"))
           payment-repo (payments/new-repository connection)
           ledger-repo (datomic/new-repository connection)
           entry (domain/payment-settled-journal payment #(UUID/randomUUID) (Instant/parse "2026-08-31T12:01:00Z"))]
       (payment-repository/save-payment! payment-repo payment {:source :source/test})
       (is (= :recorded (:outcome (ledger/record-journal! ledger-repo entry {:source :source/test}))))
       (is (= :duplicate (:outcome (ledger/record-journal! ledger-repo entry {:source :source/test}))))
       (let [journals (ledger/payment-journals ledger-repo payment-id)]
         (is (= 1 (count journals)))
         (is (= #{:posting.side/debit :posting.side/credit}
                (set (map :posting/side (:journal/postings (first journals)))))))))))
