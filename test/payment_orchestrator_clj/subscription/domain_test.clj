(ns payment-orchestrator-clj.subscription.domain-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.subscription.domain :as domain])
  (:import [java.time Instant] [java.util UUID]))

(def command {:id #uuid "11111111-1111-1111-1111-111111111111" :customer-id "customer-123"
              :amount 12990 :currency :BRL :interval :month :occurred-at (Instant/parse "2026-08-31T12:00:00Z")})

(deftest subscription-is-a-separate-active-aggregate
  (let [subscription (domain/new-subscription command)]
    (is (= :subscription.status/active (:subscription/status subscription)))
    (is (= :month (:subscription/interval subscription)))
    (is (= :subscription.status/cancelled (:subscription/status (domain/cancel subscription))))))

(deftest invoice-is-a-separate-open-aggregate-that-can-reference-a-payment
  (let [invoice (domain/issue-invoice {:id #uuid "22222222-2222-2222-2222-222222222222"
                                       :subscription-id (:id command) :amount 12990 :currency :BRL
                                       :due-at (Instant/parse "2026-09-30T23:59:59Z") :occurred-at (:occurred-at command)})]
    (is (= :invoice.status/open (:invoice/status invoice)))
    (is (= #uuid "33333333-3333-3333-3333-333333333333"
           (:invoice/payment-id (domain/attach-payment invoice #uuid "33333333-3333-3333-3333-333333333333"))))))
