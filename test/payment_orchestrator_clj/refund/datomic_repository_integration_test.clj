(ns payment-orchestrator-clj.refund.datomic-repository-integration-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.payment.datomic-repository :as payments]
            [payment-orchestrator-clj.payment.domain :as payment]
            [payment-orchestrator-clj.payment.repository :as payment-repository]
            [payment-orchestrator-clj.refund.datomic-repository :as refunds]
            [payment-orchestrator-clj.refund.repository :as refund-repository])
  (:import [java.time Instant] [java.util UUID]))

(deftest refund-history-and-provider-reference-round-trip
  (support/with-test-database
    (fn [connection]
      (let [payment-id (UUID/randomUUID)
            now (Instant/parse "2026-08-31T12:00:00Z")
            payment (payment/new-payment {:id payment-id :customer-id "customer-123" :amount 1000
                                           :currency :BRL :method :payment.method/card :occurred-at now})
            payment-repository (payments/new-repository connection)
            refund-repository (refunds/new-repository connection)
            refund {:refund/id (UUID/randomUUID) :refund/payment-id payment-id :refund/amount 400
                    :refund/status :refund.status/succeeded :refund/provider :fake
                    :refund/provider-reference "refund-123" :refund/created-at now}]
        (payment-repository/save-payment! payment-repository payment {})
        (payment-repository/record-provider-result! payment-repository payment
                                                   {:provider :fake :provider-payment/reference "payment-123"
                                                    :provider-payment/status :provider.status/succeeded
                                                    :provider-payment/raw-status "SUCCEEDED" :provider-payment/created-at now} {})
        (refund-repository/save-refund! refund-repository refund {:source :source/test})
        (is (= [refund] (refund-repository/refunds-for-payment refund-repository payment-id)))
        (is (= "payment-123" (:provider-payment/reference
                                (refund-repository/provider-payment-for refund-repository payment-id))))))))
