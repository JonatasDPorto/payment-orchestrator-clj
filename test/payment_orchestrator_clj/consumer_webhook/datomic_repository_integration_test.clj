(ns payment-orchestrator-clj.consumer-webhook.datomic-repository-integration-test
  (:require [clojure.test :refer [deftest is]] [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.consumer-webhook.service :as service] [payment-orchestrator-clj.consumer-webhook.datomic-repository :as datomic]
            [payment-orchestrator-clj.consumer-webhook.http :as http])
  (:import [java.time Instant] [java.util UUID]))
(deftest durable-delivery-lifecycle
  (support/with-test-database (fn [connection]
    (let [repo (datomic/new-repository connection) deps {:repository repo :id-generator #(UUID/randomUUID) :clock #(Instant/now) :secret "s" :sender (fn [_ _ _] {:status 500}) :max-attempts 2} event {:event/id "e1" :event/type "payment.paid"}]
      (is (= :accepted (:outcome (service/enqueue-event! deps "http://example.test" event))))
      (is (= :duplicate (:outcome (service/enqueue-event! deps "http://example.test" event))))
      (is (= 1 (count (service/pending! repo))))
      (service/deliver-pending! deps) (is (= 1 (:delivery/attempts (first (service/pending! repo)))))
      (service/deliver-pending! deps) (is (empty? (service/pending! repo)))))))

(deftest transport-failures-retry-and-then-deliver-or-dead-letter-durably
  (support/with-test-database (fn [connection]
    (let [repo (datomic/new-repository connection) base {:repository repo :id-generator #(UUID/randomUUID) :clock #(Instant/now) :secret "s" :max-attempts 2}
          failed (assoc base :sender (http/sender {:connect-timeout-ms 50 :request-timeout-ms 50}))]
      (service/enqueue-event! failed "http://127.0.0.1:1/unreachable" {:event/id "transport-retry" :event/type "payment.paid"})
      (service/deliver-pending! failed)
      (let [delivery (first (service/pending! repo))] (is (= 1 (:delivery/attempts delivery))) (is (= :delivery.status/pending (:delivery/status delivery))))
      (service/deliver-pending! failed)
      (is (empty? (service/pending! repo)))
      (service/enqueue-event! failed "http://127.0.0.1:1/unreachable" {:event/id "transport-success" :event/type "payment.paid"})
      (service/deliver-pending! (assoc base :sender (fn [_ _ _] {:status 204})))
      (is (empty? (service/pending! repo)))))))
