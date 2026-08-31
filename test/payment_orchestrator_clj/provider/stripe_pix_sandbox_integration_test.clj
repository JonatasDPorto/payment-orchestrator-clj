(ns payment-orchestrator-clj.provider.stripe-pix-sandbox-integration-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.provider.port :as port]
            [payment-orchestrator-clj.provider.stripe.adapter :as stripe])
  (:import [java.util UUID]))

(deftest ^:integration sandbox-pix-payment-intent-returns-a-canonical-qr-action
  (let [gateway (stripe/new-gateway {})
        result (port/create-payment! gateway {:operation/id (UUID/randomUUID)
                                              :payment/id (UUID/randomUUID)
                                              :amount 100
                                              :currency :BRL
                                              :method :payment.method/pix
                                              :pix {:tax-id "000.000.000-00"
                                                    :email "succeed_immediately@example.com"
                                                    :name "Stripe Test"}})]
    (is (= :stripe (:provider result)))
    (is (= :provider.status/requires-action (:provider-payment/status result)))
    (is (= :pix/qr-code (get-in result [:provider-payment/action :action/type])))
    (is (string? (get-in result [:provider-payment/action :action/payload])))
    (is (instance? java.time.Instant (get-in result [:provider-payment/action :action/expires-at])))))
