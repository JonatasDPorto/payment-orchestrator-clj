(ns payment-orchestrator-clj.payment.provider-integration-test
  (:require [clojure.test :refer [deftest is]]
            [datomic.client.api :as d]
            [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.payment.datomic-repository :as datomic-repository]
            [payment-orchestrator-clj.payment.service :as service]
            [payment-orchestrator-clj.provider.fake :as fake])
  (:import [java.time Instant]
           [java.util UUID]))

(deftest provider-result-is-persisted
  (support/with-test-database
   (fn [connection]
     (let [deps {:payments (datomic-repository/new-repository connection)
                 :gateway (fake/new-gateway {:mode :always-success})
                 :clock #(Instant/parse "2026-08-30T12:00:00Z")
                 :id-generator #(UUID/randomUUID)}
           result (service/create-payment-idempotently!
                   deps {:customer-id "customer-123" :amount 12990 :currency :BRL
                         :method :payment.method/card}
                   "provider-key" {:source :source/test})
           payment-id (get-in result [:payment :payment/id])]
       (is (= :created (:outcome result)))
       (is (= :payment.status/processing (get-in result [:payment :payment/status])))
       (is (some? (get-in result [:provider-result :provider-payment/reference])))
       (is (= [[1]]
              (d/q '[:find (count ?provider-payment)
                     :in $ ?payment-id
                     :where
                     [?provider-payment :provider-payment/payment ?payment]
                     [?payment :payment/id ?payment-id]]
                   (d/db connection) payment-id)))))))
