(ns payment-orchestrator-clj.provider.stripe-adapter-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.provider.contract-test :as contract]
            [payment-orchestrator-clj.provider.port :as port]
            [payment-orchestrator-clj.provider.stripe.adapter :as stripe]
            [payment-orchestrator-clj.provider.stripe.errors :as errors]))

(defn- stub-response [{:keys [path]}]
  (cond
    (= path "/v1/payment_intents") {:status 200 :request-id "req_create"
                                      :body {:id "pi_contract" :status "succeeded"}}
    (= path "/v1/payment_intents/pi_contract") {:status 200 :request-id "req_fetch"
                                                   :body {:id "pi_contract" :status "succeeded"}}
    (= path "/v1/payment_intents/pi_contract/cancel") {:status 200 :request-id "req_cancel"
                                                          :body {:id "pi_contract" :status "canceled"}}
    (= path "/v1/refunds") {:status 200 :request-id "req_refund"
                              :body {:id "re_contract" :payment_intent "pi_contract" :status "succeeded"}}))

(deftest stripe-adapter-satisfies-the-shared-gateway-contract
  (contract/run-contract (stripe/new-gateway {:request-handler stub-response})))

(deftest stripe-adapter-sends-an-operation-scoped-outbound-key
  (let [requests (atom [])
        gateway (stripe/new-gateway {:request-handler #(do (swap! requests conj %)
                                                           {:status 200 :request-id "req_create"
                                                            :body {:id "pi_key" :status "succeeded"}})})]
    (port/create-payment!
     gateway {:operation/id #uuid "0c8b8d05-09a0-44c4-bd44-f831ac8958ce"
              :payment/id #uuid "9c8b8d05-09a0-44c4-bd44-f831ac8958ce"
              :amount 100 :currency :BRL})
    (is (= "payment-orchestrator-clj:create-payment:0c8b8d05-09a0-44c4-bd44-f831ac8958ce"
           (:idempotency-key (first @requests))))))

(deftest stripe-errors-have-canonical-categories
  (let [error (errors/response-error {:status 402 :request-id "req_declined"
                                      :body {:error {:code "card_declined"}}})]
    (is (= :provider.error/declined (:provider/error (ex-data error))))
    (is (= :stripe (:provider (ex-data error))))
    (is (true? (:outcome-known? (ex-data error))))))
