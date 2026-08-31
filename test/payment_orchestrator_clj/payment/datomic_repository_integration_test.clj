(ns payment-orchestrator-clj.payment.datomic-repository-integration-test
  (:require [clojure.test :refer [deftest is]]
            [datomic.client.api :as d]
            [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.payment.datomic-repository :as datomic-repository]
            [payment-orchestrator-clj.payment.domain :as payment]
            [payment-orchestrator-clj.payment.repository :as repository])
  (:import [java.time Instant]))

(def payment-id #uuid "0c66f7b8-4fb9-4f08-8da0-9b577bd6b5df")
(def created-at (Instant/parse "2026-08-30T12:00:00Z"))

(def test-payment
  (payment/new-payment {:id payment-id
                        :customer-id "customer-123"
                        :amount 12990
                        :currency :BRL
                        :method :payment.method/card
                        :occurred-at created-at}))

(deftest save-and-find-preserves-payment-business-fields
  (support/with-test-database
    (fn [connection]
      (let [payments (datomic-repository/new-repository connection)]
        (repository/save-payment! payments test-payment
                                  {:request-id "request-123"
                                   :correlation-id "correlation-123"
                                   :source :source/test})
        (is (= (assoc test-payment :payment/events [] :payment/merchant-id "default")
               (repository/find-payment payments payment-id)))
        (is (= [["request-123" "correlation-123" :source/test]]
               (d/q '[:find ?request-id ?correlation-id ?source
                      :in $ ?payment-id
                      :where
                      [?payment :payment/id ?payment-id ?tx]
                      [?tx :tx/request-id ?request-id]
                      [?tx :tx/correlation-id ?correlation-id]
                      [?tx :tx/source ?source]]
                    (d/db connection)
                    payment-id)))))))

(deftest saving-the-same-payment-id-upserts-one-entity
  (support/with-test-database
    (fn [connection]
      (let [payments (datomic-repository/new-repository connection)]
        (repository/save-payment! payments test-payment)
        (repository/save-payment! payments test-payment)
        (is (= [[1]]
               (d/q '[:find (count ?payment)
                      :in $ ?payment-id
                      :where [?payment :payment/id ?payment-id]]
                    (d/db connection)
                    payment-id)))))))

(deftest pix-action-round-trips-as-a-canonical-component
  (support/with-test-database
    (fn [connection]
      (let [payments (datomic-repository/new-repository connection)
            pix-payment (assoc test-payment
                               :payment/method :payment.method/pix
                               :payment/action {:payment-action/type :pix/qr-code
                                                :payment-action/payload "000201PIXTESTPAYLOAD6304ABCD"
                                                :payment-action/qr-code-url "https://example.test/pix.png"
                                                :payment-action/hosted-instructions-url "https://example.test/pix"
                                                :payment-action/expires-at (Instant/parse "2030-01-01T00:00:00Z")})]
        (repository/save-payment! payments pix-payment)
        (is (= (assoc pix-payment :payment/events [] :payment/merchant-id "default")
               (repository/find-payment payments payment-id)))))))

(deftest boleto-action-round-trips-as-a-canonical-component
  (support/with-test-database
   (fn [connection]
     (let [payments (datomic-repository/new-repository connection)
           boleto-payment (assoc test-payment :payment/method :payment.method/boleto
                                 :payment/action {:payment-action/type :boleto/voucher
                                                  :payment-action/payload "00190500954014481606906809350314337370000000100"
                                                  :payment-action/hosted-instructions-url "https://example.test/boleto"
                                                  :payment-action/document-url "https://example.test/boleto.pdf"
                                                  :payment-action/expires-at (Instant/parse "2030-01-03T23:59:59Z")})]
       (repository/save-payment! payments boleto-payment)
       (is (= (assoc boleto-payment :payment/events [] :payment/merchant-id "default")
              (repository/find-payment payments payment-id)))))))
