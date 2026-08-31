(ns payment-orchestrator-clj.webhook-integration-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.payment.datomic-repository :as payment-repository]
            [payment-orchestrator-clj.payment.domain :as domain]
            [payment-orchestrator-clj.payment.repository :as payments]
            [payment-orchestrator-clj.ledger.datomic-repository :as ledger-repository]
            [payment-orchestrator-clj.ledger.repository :as ledger]
            [payment-orchestrator-clj.webhook.datomic-repository :as event-repository]
            [payment-orchestrator-clj.webhook.repository :as events]
            [payment-orchestrator-clj.webhook.service :as service])
  (:import [java.time Instant]
           [java.util UUID]))

(def fixed-clock #(Instant/parse "2026-08-31T12:00:00Z"))

(def webhook-body
  "{\"id\":\"evt_integration_1\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_integration\"}}}")

(deftest duplicate-provider-event-produces-one-datomic-payment-transition
  (support/with-test-database
   (fn [connection]
     (let [payment-id (UUID/randomUUID)
           payments-repository (payment-repository/new-repository connection)
           provider-events (event-repository/new-repository connection)
           ledger-repository (ledger-repository/new-repository connection)
           payment (domain/transition
                    (domain/new-payment {:id payment-id :customer-id "customer-123" :amount 100
                                         :currency :BRL :method :payment.method/card :occurred-at (fixed-clock)})
                    :payment.status/processing (fixed-clock))
           dependencies {:payments payments-repository :provider-events provider-events :ledger ledger-repository
                         :clock fixed-clock :id-generator #(UUID/randomUUID)}]
       (payments/save-payment! payments-repository payment {:source :source/test})
       (payments/record-provider-result! payments-repository payment
                                         {:provider :stripe :provider-payment/reference "pi_integration"
                                          :provider-payment/status :provider.status/processing
                                          :provider-payment/raw-status "processing"
                                         :provider-payment/created-at (fixed-clock)}
                                         {:source :source/test})
       (is (some? (events/payment-by-provider-reference provider-events :stripe "pi_integration")))
       (is (= :accepted (:outcome (service/enqueue-stripe-event! dependencies webhook-body))))
       (is (= :duplicate (:outcome (service/enqueue-stripe-event! dependencies webhook-body))))
       (service/process-pending! dependencies)
       (is (= :payment.status/paid (:payment/status (payments/find-payment payments-repository payment-id))))
       (is (= 1 (count (ledger/payment-journals ledger-repository payment-id))))
       (is (empty? (events/pending-events provider-events)))))))
