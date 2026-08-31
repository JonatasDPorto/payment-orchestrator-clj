(ns payment-orchestrator-clj.reconciliation-integration-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.payment.datomic-repository :as payment-repository]
            [payment-orchestrator-clj.payment.repository :as payments]
            [payment-orchestrator-clj.payment.service :as payment-service]
            [payment-orchestrator-clj.provider.fake :as fake]
            [payment-orchestrator-clj.reconciliation.datomic-repository :as operation-repository]
            [payment-orchestrator-clj.reconciliation.repository :as operations]
            [payment-orchestrator-clj.reconciliation.service :as reconciliation])
  (:import [java.time Instant] [java.util UUID]))

(def fixed-clock #(Instant/parse "2026-08-31T14:00:00Z"))

(deftest commit-then-timeout-is-reconciled-without-a-second-provider-create
  (support/with-test-database
   (fn [connection]
     (let [payments-repository (payment-repository/new-repository connection)
           operations-repository (operation-repository/new-repository connection)
           gateway (fake/new-gateway {:mode :commit-then-timeout})
           dependencies {:payments payments-repository :operations operations-repository :gateway gateway
                         :clock fixed-clock :id-generator #(UUID/randomUUID)}
           command {:customer-id "customer-ambiguous" :amount 1000 :currency :BRL :method :payment.method/card}]
       (try
         (payment-service/create-payment-idempotently! dependencies command "ambiguous-key" {:source :source/test})
         (is false "The caller must observe the ambiguous provider timeout")
         (catch clojure.lang.ExceptionInfo error
           (is (= :provider.error/timeout (:provider/error (ex-data error))))))
       (let [operation (first (operations/unresolved-operations operations-repository))
             payment-id (get-in operation [:provider-operation/payment :payment/id])]
         (is (= :payment.status/processing (:payment/status (payments/find-payment payments-repository payment-id))))
         (is (= :provider-operation.status/outcome-unknown (:provider-operation/status operation)))
         (is (= [:reconciliation.result/corrected] (reconciliation/run! dependencies)))
         (is (= :payment.status/paid (:payment/status (payments/find-payment payments-repository payment-id))))
         (is (empty? (operations/unresolved-operations operations-repository)))
         (is (= :replayed (:outcome (payment-service/create-payment-idempotently!
                                     dependencies command "ambiguous-key" {:source :source/test})))))))))

(deftest timeout-before-provider-commit-remains-for-manual-review-not-a-blind-retry
  (support/with-test-database
   (fn [connection]
     (let [payments-repository (payment-repository/new-repository connection)
           operations-repository (operation-repository/new-repository connection)
           dependencies {:payments payments-repository :operations operations-repository
                         :gateway (fake/new-gateway {:mode :timeout})
                         :clock fixed-clock :id-generator #(UUID/randomUUID)}]
       (try (payment-service/create-payment-idempotently! dependencies
                                                          {:customer-id "customer-timeout" :amount 1000 :currency :BRL :method :payment.method/card}
                                                          "timeout-key" {:source :source/test})
            (catch clojure.lang.ExceptionInfo _))
       (is (= [:reconciliation.result/deferred] (reconciliation/run! dependencies)))
       (is (= 1 (count (operations/unresolved-operations operations-repository))))))))

(deftest unavailable-provider-during-reconciliation-preserves-the-ambiguous-operation
  (support/with-test-database
   (fn [connection]
     (let [payments-repository (payment-repository/new-repository connection)
           operations-repository (operation-repository/new-repository connection)
           dependencies {:payments payments-repository :operations operations-repository
                         :gateway (fake/new-gateway {:mode :commit-then-timeout-fetch-unavailable})
                         :clock fixed-clock :id-generator #(UUID/randomUUID)}]
       (try (payment-service/create-payment-idempotently! dependencies
                                                          {:customer-id "customer-unavailable" :amount 1000 :currency :BRL :method :payment.method/card}
                                                          "unavailable-key" {:source :source/test})
            (catch clojure.lang.ExceptionInfo _))
       (is (= [:reconciliation.result/deferred] (reconciliation/run! dependencies)))
       (is (= 1 (count (operations/unresolved-operations operations-repository))))))))
