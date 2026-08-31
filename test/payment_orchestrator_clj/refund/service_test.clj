(ns payment-orchestrator-clj.refund.service-test
  (:require [clojure.test :refer [deftest is testing]]
            [payment-orchestrator-clj.payment.repository :as payment-repository]
            [payment-orchestrator-clj.refund.repository :as refund-repository]
            [payment-orchestrator-clj.refund.service :as service]
            [payment-orchestrator-clj.provider.fake :as fake])
  (:import [java.time Instant] [java.util UUID]))

(defn- fixture []
  (let [payment-id (UUID/randomUUID)
        payments (atom {payment-id {:payment/id payment-id :payment/merchant-id "default"
                                    :payment/amount 1000 :payment/currency :BRL
                                    :payment/method :payment.method/card :payment/status :payment.status/paid}})
        refunds (atom [])
        payment-store (reify payment-repository/PaymentRepository
                        (save-payment! [_ payment] payment)
                        (save-payment! [_ payment _] (swap! payments assoc (:payment/id payment) payment) payment)
                        (create-payment-idempotently! [_ payment _ _] {:outcome :created :payment payment})
                        (record-provider-result! [_ payment _ _] payment)
                        (find-payment [_ id] (get @payments id)))
        refund-store (reify refund-repository/RefundRepository
                       (save-refund! [_ refund _] (swap! refunds conj refund) refund)
                       (find-refund [_ refund-id] (some #(when (= refund-id (:refund/id %)) %) @refunds))
                       (refunds-for-payment [_ id] (filterv #(= id (:refund/payment-id %)) @refunds))
                       (provider-payment-for [_ _] {:provider-payment/provider :fake :provider-payment/reference "fake-payment"})
                       (record-reconciliation! [_ reconciliation _] reconciliation))]
    {:payment-id payment-id :refunds refunds
     :dependencies {:payments payment-store :refunds refund-store :gateway (fake/new-gateway {})
                    :clock #(Instant/parse "2026-08-31T12:00:00Z") :id-generator #(UUID/randomUUID)}}))

(deftest partial-and-multiple-refunds-update-the-payment-once-per-refund
  (let [{:keys [payment-id refunds dependencies]} (fixture)
        first-result (service/refund-payment! dependencies payment-id 400 {})
        second-result (service/refund-payment! dependencies payment-id 600 {})]
    (is (= :payment.status/partially-refunded (get-in first-result [:payment :payment/status])))
    (is (= :payment.status/refunded (get-in second-result [:payment :payment/status])))
    (is (= 1000 (reduce + (map :refund/amount @refunds))))
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"payment-not-refundable"
                          (service/refund-payment! dependencies payment-id 1 {})))))

(deftest reconciliation-records-a-mismatch-without-changing-financial-history
  (let [{:keys [payment-id dependencies]} (fixture)]
    (service/refund-payment! dependencies payment-id 400 {})
    (is (= :mismatch
           (:refund-reconciliation/result
            (service/reconcile-payment-refunds! dependencies payment-id 1000
                                                 [{:refund/amount 500}] {}))))))

(deftest concurrent-refund-requests-never-exceed-the-captured-amount
  (let [{:keys [payment-id refunds dependencies]} (fixture)
        outcomes (->> (repeatedly 20 #(future (try (service/refund-payment! dependencies payment-id 100 {})
                                                    (catch clojure.lang.ExceptionInfo _ :rejected))))
                      (mapv deref))]
    (is (= 10 (count (remove #{:rejected} outcomes))))
    (is (= 1000 (reduce + (map :refund/amount @refunds))))
    (is (every? #(<= % 1000) (reductions + (map :refund/amount @refunds))))))
