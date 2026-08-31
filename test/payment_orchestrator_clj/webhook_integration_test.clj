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

(deftest pix-webhooks-transition-asynchronously-and-are-idempotent
  (support/with-test-database
   (fn [connection]
     (let [payment-id (UUID/randomUUID)
           payments-repository (payment-repository/new-repository connection)
           provider-events (event-repository/new-repository connection)
           ledger-repository (ledger-repository/new-repository connection)
           payment (-> (domain/new-payment {:id payment-id :customer-id "customer-123" :amount 100
                                            :currency :BRL :method :payment.method/pix :occurred-at (fixed-clock)})
                       (domain/transition :payment.status/processing (fixed-clock))
                       (domain/transition :payment.status/requires-action (fixed-clock)))
           dependencies {:payments payments-repository :provider-events provider-events :ledger ledger-repository
                         :clock fixed-clock :id-generator #(UUID/randomUUID)}
           succeeded "{\"id\":\"evt_pix_succeeded\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_pix_integration\"}}}"
           failed "{\"id\":\"evt_pix_failed\",\"type\":\"payment_intent.payment_failed\",\"data\":{\"object\":{\"id\":\"pi_pix_integration_failed\"}}}"]
       (payments/save-payment! payments-repository payment {:source :source/test})
       (payments/record-provider-result! payments-repository payment
                                         {:provider :stripe :provider-payment/reference "pi_pix_integration"
                                          :provider-payment/status :provider.status/requires-action
                                          :provider-payment/raw-status "requires_action"
                                          :provider-payment/created-at (fixed-clock)}
                                         {:source :source/test})
       (is (= :accepted (:outcome (service/enqueue-stripe-event! dependencies succeeded))))
       (is (= :duplicate (:outcome (service/enqueue-stripe-event! dependencies succeeded))))
       (service/process-pending! dependencies)
       (is (= :payment.status/paid (:payment/status (payments/find-payment payments-repository payment-id))))
       (let [failed-payment-id (UUID/randomUUID)
             failed-payment (-> (domain/new-payment {:id failed-payment-id :customer-id "customer-456" :amount 100
                                                      :currency :BRL :method :payment.method/pix :occurred-at (fixed-clock)})
                                (domain/transition :payment.status/processing (fixed-clock))
                                (domain/transition :payment.status/requires-action (fixed-clock)))]
         (payments/save-payment! payments-repository failed-payment {:source :source/test})
         (payments/record-provider-result! payments-repository failed-payment
                                           {:provider :stripe :provider-payment/reference "pi_pix_integration_failed"
                                            :provider-payment/status :provider.status/requires-action
                                            :provider-payment/raw-status "requires_action"
                                            :provider-payment/created-at (fixed-clock)}
                                           {:source :source/test})
         (is (= :accepted (:outcome (service/enqueue-stripe-event! dependencies failed))))
         (service/process-pending! dependencies)
         (is (= :payment.status/failed (:payment/status (payments/find-payment payments-repository failed-payment-id)))))))))
