(ns payment-orchestrator-clj.ledger.domain-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check :as check]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [payment-orchestrator-clj.ledger.domain :as ledger])
  (:import [java.time Instant]
           [java.util UUID]))

(defn- journal [debit credit]
  {:journal/id (UUID/randomUUID) :journal/payment (UUID/randomUUID)
   :journal/type :journal.type/payment-settled :journal/dedupe-key (str (UUID/randomUUID))
   :journal/created-at (Instant/now)
   :journal/postings [{:posting/id (UUID/randomUUID) :posting/account ledger/processor-receivable
                       :posting/side :posting.side/debit :posting/amount debit :posting/currency :BRL}
                      {:posting/id (UUID/randomUUID) :posting/account ledger/merchant-payable
                       :posting/side :posting.side/credit :posting/amount credit :posting/currency :BRL}]})

(deftest balanced-journal-is-accepted
  (is (= 100 (-> (ledger/journal (journal 100 100)) :journal/postings first :posting/amount))))

(deftest unbalanced-journal-is-rejected
  (try
    (ledger/journal (journal 100 99))
    (is false "Expected an unbalanced journal error")
    (catch clojure.lang.ExceptionInfo error
      (is (= :ledger/unbalanced-journal (:error/code (ex-data error)))))))

(deftest generated-settlement-journals-are-balanced
  (let [result (check/quick-check 100
                                  (prop/for-all [amount (gen/choose 1 1000000)]
                                    (let [entry (ledger/payment-settled-journal
                                                 {:payment/id (UUID/randomUUID) :payment/amount amount :payment/currency :BRL}
                                                 #(UUID/randomUUID) (Instant/now))]
                                      (and (ledger/balanced? (:journal/postings entry))
                                           (= entry (ledger/journal entry))))))]
    (is (:pass? result) (pr-str result))))
