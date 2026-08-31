(ns payment-orchestrator-clj.provider.stripe-sandbox-integration-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.provider.port :as port]
            [payment-orchestrator-clj.provider.stripe.adapter :as stripe])
  (:import [java.util UUID]))

(deftest ^:integration sandbox-payment-intent-is-canonical
  (let [gateway (stripe/new-gateway {})
        result (port/create-payment! gateway {:operation/id (UUID/randomUUID)
                                              :payment/id (UUID/randomUUID)
                                              :amount 100
                                              :currency :BRL
                                              :method :payment.method/card})]
    (is (= :stripe (:provider result)))
    (is (some? (:provider-payment/reference result)))
    (is (contains? port/provider-statuses (:provider-payment/status result)))))
