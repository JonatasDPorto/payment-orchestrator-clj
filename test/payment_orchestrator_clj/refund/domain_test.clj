(ns payment-orchestrator-clj.refund.domain-test
  (:require [clojure.test :refer [deftest is]] [clojure.test.check :as check]
            [clojure.test.check.generators :as gen] [clojure.test.check.properties :as prop]
            [payment-orchestrator-clj.refund.domain :as domain])
  (:import [java.time Instant]))
(def base {:payment-id #uuid "11111111-1111-1111-1111-111111111111" :captured-amount 1000 :occurred-at (Instant/parse "2026-08-31T00:00:00Z")})
(deftest supports-partial-and-multiple-refunds-without-over-refunding
  (let [first (domain/new-refund (assoc base :id #uuid "22222222-2222-2222-2222-222222222222" :amount 400 :existing-refunds []))
        second (domain/new-refund (assoc base :id #uuid "33333333-3333-3333-3333-333333333333" :amount 600 :existing-refunds [first]))]
    (is (= 1000 (domain/refunded-amount [first second])))
    (is (= :payment.status/refunded (domain/payment-status-after-refund 1000 [first second])))
    (is (thrown? clojure.lang.ExceptionInfo (domain/new-refund (assoc base :id #uuid "44444444-4444-4444-4444-444444444444" :amount 1 :existing-refunds [first second]))))))
(deftest refund-total-never-exceeds-captured-property
  (let [property (check/quick-check 100 (prop/for-all [captured (gen/choose 1 100000) amounts (gen/vector (gen/choose 1 100000) 0 20)]
                                          (<= (domain/refunded-amount (loop [remaining captured xs amounts refunds []]
                                                                        (if-let [amount (first xs)]
                                                                          (if (<= amount remaining)
                                                                            (recur (- remaining amount) (rest xs) (conj refunds {:refund/amount amount}))
                                                                            refunds)
                                                                          refunds))) captured)))]
    (is (:pass? property))))

(deftest refund-reconciliation-detects-provider-mismatch
  (is (= :mismatch (:refund-reconciliation/result
                    (domain/reconcile 1000 [{:refund/amount 400}] [{:refund/amount 500}])))))
