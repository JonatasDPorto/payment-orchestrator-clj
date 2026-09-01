(ns payment-orchestrator-clj.provider.asaas-mapper-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.provider.asaas.mapper :as mapper]))

(def command
  {:operation/id #uuid "0c8b8d05-09a0-44c4-bd44-f831ac8958ce"
   :payment/id #uuid "9c8b8d05-09a0-44c4-bd44-f831ac8958ce"
   :amount 12990 :currency :BRL
   :customer {:reference "cus_contract"}})

(deftest create-request-translates-canonical-values-without-card-data
  (let [request (mapper/create-request (assoc command :method :payment.method/card) "2030-01-02")]
    (is (= :post (:method request)))
    (is (= "/payments" (:path request)))
    (is (= {:customer "cus_contract" :billingType "CREDIT_CARD" :value 129.90M
            :dueDate "2030-01-02" :externalReference "9c8b8d05-09a0-44c4-bd44-f831ac8958ce"}
           (:body request)))
    (is (= "payment-orchestrator-clj:create-payment:0c8b8d05-09a0-44c4-bd44-f831ac8958ce"
           (:idempotency-key request)))))

(deftest request-methods-and-statuses-are-canonical
  (is (= "PIX" (get-in (mapper/create-request (assoc command :method :payment.method/pix) "2030-01-02") [:body :billingType])))
  (is (= "BOLETO" (get-in (mapper/create-request (assoc command :method :payment.method/boleto) "2030-01-02") [:body :billingType])))
  (is (= {:method :delete :path "/payments/pay_123"}
         (select-keys (mapper/cancel-request {:operation/id (:operation/id command)
                                              :provider-payment/reference "pay_123"}) [:method :path])))
  (is (= {:method :post :path "/payments/pay_123/refund" :body {:value 12.34M}}
         (select-keys (mapper/refund-request {:operation/id (:operation/id command)
                                              :provider-payment/reference "pay_123" :refund/amount 1234}) [:method :path :body])))
  (is (= :provider.status/processing
         (:provider-payment/status (mapper/payment->provider-result {:body {:id "pay_123" :status "PENDING"}}))))
  (is (= :provider.status/succeeded
         (:provider-payment/status (mapper/payment->provider-result {:body {:id "pay_123" :status "RECEIVED"}}))))
  (is (= :provider.status/cancelled
         (:provider-payment/status (mapper/deleted-payment->provider-result {:body {:id "pay_123" :deleted true}})))))
