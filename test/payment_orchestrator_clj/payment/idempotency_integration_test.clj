(ns payment-orchestrator-clj.payment.idempotency-integration-test
  (:require [clojure.test :refer [deftest is]]
            [datomic.client.api :as d]
            [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.payment.datomic-repository :as datomic-repository]
            [payment-orchestrator-clj.payment.service :as service]
            [payment-orchestrator-clj.provider.fake :as fake])
  (:import [java.time Instant]
           [java.util UUID]
           [java.util.concurrent CountDownLatch]))

(def command
  {:customer-id "customer-123"
   :amount 12990
   :currency :BRL
   :method :payment.method/card})

(def context
  {:request-id "request-123"
   :correlation-id "correlation-123"
   :source :source/test})

(defn dependencies [connection]
  {:payments (datomic-repository/new-repository connection)
   :clock #(Instant/parse "2026-08-30T12:00:00Z")
   :id-generator #(UUID/randomUUID)
   :gateway (fake/new-gateway {:mode :always-success})})

(defn create! [dependencies key payment-command]
  (service/create-payment-idempotently! dependencies payment-command key context))

(deftest one-hundred-sequential-retries-create-one-payment
  (support/with-test-database
   (fn [connection]
     (let [results (doall (repeatedly 100 #(create! (dependencies connection) "sequential-key" command)))]
       (is (= 1 (count (set (map #(get-in % [:payment :payment/id]) results)))))
       (is (= 1 (count (filter #(= :created (:outcome %)) results))))
       (is (= 99 (count (filter #(= :replayed (:outcome %)) results))))))))

(deftest same-key-with-different-command-conflicts
  (support/with-test-database
   (fn [connection]
     (let [deps (dependencies connection)]
       (is (= :created (:outcome (create! deps "conflict-key" (assoc command :amount 1000)))))
       (is (= :conflict (:outcome (create! deps "conflict-key" (assoc command :amount 2000)))))))))

(deftest one-hundred-concurrent-retries-create-one-payment
  (support/with-test-database
   (fn [connection]
     (let [deps (dependencies connection)
           start (CountDownLatch. 1)
           attempts (doall
                     (repeatedly 100
                                 #(future
                                    (.await start)
                                    (create! deps "concurrent-key" command))))]
       (.countDown start)
       (let [results (mapv deref attempts)
             ids (set (map #(get-in % [:payment :payment/id]) results))]
         (is (= 1 (count ids)))
         (is (= 1 (count (filter #(= :created (:outcome %)) results))))
         (is (= 99 (count (filter #(= :replayed (:outcome %)) results))))
         (is (= [[1]]
                (d/q '[:find (count ?payment)
                       :in $ ?idempotency-key
                       :where
                       [?idempotency :idempotency/key ?idempotency-key]
                       [?idempotency :idempotency/payment ?payment]]
                     (d/db connection)
                     "concurrent-key"))))))))
