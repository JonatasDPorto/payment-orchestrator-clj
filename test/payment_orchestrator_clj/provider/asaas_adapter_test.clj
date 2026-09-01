(ns payment-orchestrator-clj.provider.asaas-adapter-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.provider.asaas.adapter :as asaas]
            [payment-orchestrator-clj.provider.contract-test :as contract]
            [payment-orchestrator-clj.provider.port :as port]))

(defn- stub-response [{:keys [method path]}]
  (cond
    (= [:post "/payments"] [method path]) {:status 200 :body {:id "pay_contract" :status "PENDING"}}
    (= [:get "/payments/pay_contract"] [method path]) {:status 200 :body {:id "pay_contract" :status "PENDING"}}
    (= [:delete "/payments/pay_contract"] [method path]) {:status 200 :body {:id "pay_contract" :deleted true}}
    (= [:post "/payments/pay_contract/refund"] [method path]) {:status 200 :body {:id "pay_contract" :status "REFUNDED"}}))

(deftest asaas-adapter-satisfies-the-shared-gateway-contract
  (contract/run-contract (asaas/new-gateway {:customer-id "cus_contract" :due-date "2030-01-02" :request-handler stub-response})))

(deftest asaas-adapter-preserves-the-canonical-provider-boundary
  (let [requests (atom [])
        gateway (asaas/new-gateway {:due-date "2030-01-02"
                                    :request-handler #(do (swap! requests conj %)
                                                          {:status 200 :body {:id "pay_123" :status "PENDING"}})})
        result (port/create-payment! gateway {:operation/id #uuid "0c8b8d05-09a0-44c4-bd44-f831ac8958ce"
                                              :payment/id #uuid "9c8b8d05-09a0-44c4-bd44-f831ac8958ce"
                                              :amount 100 :currency :BRL :method :payment.method/card
                                              :customer {:reference "cus_123"}})]
    (is (= :asaas (:provider result)))
    (is (= :provider.status/processing (:provider-payment/status result)))
    (is (= "cus_123" (get-in (first @requests) [:body :customer])))))
