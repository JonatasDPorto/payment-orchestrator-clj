(ns payment-orchestrator-clj.audit-integration-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.audit.datomic-repository :as audit]
            [payment-orchestrator-clj.audit.repository :as audit-repository]
            [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.payment.datomic-repository :as datomic]
            [payment-orchestrator-clj.payment.domain :as domain]
            [payment-orchestrator-clj.payment.repository :as payments])
  (:import [java.time Instant]
           [java.util UUID]))

(defn- transition [payment status at]
  (domain/transition payment status (Instant/parse at)))

(deftest history-and-as-of-reconstruct-payment-state-with-transaction-context
  (support/with-test-database
   (fn [connection]
     (let [payment-id (UUID/randomUUID)
           created (domain/new-payment {:id payment-id :customer-id "customer-audit" :amount 100
                                        :currency :BRL :method :payment.method/card
                                        :occurred-at (Instant/parse "2026-08-31T10:00:00Z")})
           processing (transition created :payment.status/processing "2026-08-31T10:01:00Z")
           paid (transition processing :payment.status/paid "2026-08-31T10:02:00Z")
           repository (datomic/new-repository connection)
           audit-repository (audit/new-repository connection)]
       (payments/save-payment! repository created {:request-id "request-created" :correlation-id "corr-1"
                                                    :actor :actor/test :source :source/test
                                                    :reason :reason/payment-create :event-type :event/payment-created})
       (payments/save-payment! repository processing {:request-id "request-processing" :correlation-id "corr-1"
                                                       :actor :actor/test :source :source/test
                                                       :reason :reason/provider-result :event-type :event/payment-status-changed})
       (payments/save-payment! repository paid {:request-id "request-paid" :correlation-id "corr-1"
                                                :actor :actor/test :source :source/test
                                                :reason :reason/provider-webhook :event-type :event/payment-status-changed})
       (let [history (audit-repository/payment-history audit-repository payment-id)
             asserted (filter :audit/added? history)
             created-event (first asserted)]
         (is (= :payment.status/paid (:payment/status (payments/find-payment repository payment-id))))
         (is (= [:payment.status/created :payment.status/processing :payment.status/paid]
                (mapv :audit/status asserted)))
         (is (= "request-paid" (:audit/request-id (last asserted))))
         (is (= :reason/provider-webhook (:audit/reason (last asserted))))
         (is (= :payment.status/created
                (:payment/status (audit-repository/payment-as-of audit-repository payment-id
                                                                   (:audit/transaction created-event)))))
         (is (some false? (map :audit/added? history))))))))
