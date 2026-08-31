(ns payment-orchestrator-clj.provider.stripe-boleto-sandbox-integration-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.provider.port :as port]
            [payment-orchestrator-clj.provider.stripe.adapter :as stripe])
  (:import [java.time Instant]
           [java.util UUID]))

(deftest ^:integration sandbox-boleto-payment-intent-returns-a-canonical-voucher-action
  (let [gateway (stripe/new-gateway {})
        result (port/create-payment! gateway {:operation/id (UUID/randomUUID)
                                              :payment/id (UUID/randomUUID)
                                              :amount 1000
                                              :currency :BRL
                                              :method :payment.method/boleto
                                              :boleto {:tax-id "000.000.000-00"
                                                       :email "succeed_immediately@example.com"
                                                       :name "Stripe Test"
                                                       :address {:line1 "1234 Av Paulista"
                                                                 :city "Sao Paulo"
                                                                 :state "SP"
                                                                 :postal-code "01310-000"
                                                                 :country "BR"}}})]
    (is (= :stripe (:provider result)))
    (is (= :provider.status/requires-action (:provider-payment/status result)))
    (is (= :boleto/voucher (get-in result [:provider-payment/action :action/type])))
    (is (string? (get-in result [:provider-payment/action :action/payload])))
    (is (string? (get-in result [:provider-payment/action :action/hosted-instructions-url])))
    (is (instance? Instant (get-in result [:provider-payment/action :action/expires-at])))))
