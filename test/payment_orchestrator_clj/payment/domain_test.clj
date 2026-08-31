(ns payment-orchestrator-clj.payment.domain-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.test.check :as check]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [payment-orchestrator-clj.payment.domain :as payment])
  (:import [java.time Instant]))

(def payment-id #uuid "2ee9a79d-8ccf-4c75-89a2-beb89b271ca1")
(def occurred-at (Instant/parse "2026-08-30T12:00:00Z"))
(def valid-command {:id payment-id :customer-id "customer-123" :amount 12990 :currency :BRL
                    :method :payment.method/card :occurred-at occurred-at})

(defn ex-data-for [f]
  (try (f) nil (catch clojure.lang.ExceptionInfo exception (ex-data exception))))

(deftest creates-a-canonical-payment
  (let [created (payment/new-payment valid-command)]
    (is (= :payment.status/created (:payment/status created)))
    (is (= 12990 (:payment/amount created)))
    (is (= [{:event/type :payment/created :event/payment-id payment-id :event/occurred-at occurred-at}]
           (:payment/events created)))))

(deftest accepts-the-canonical-pix-payment-method
  (is (= :payment.method/pix
         (:payment/method (payment/new-payment (assoc valid-command :method :payment.method/pix))))))

(deftest accepts-the-canonical-boleto-payment-method
  (is (= :payment.method/boleto
         (:payment/method (payment/new-payment (assoc valid-command :method :payment.method/boleto))))))

(deftest rejects-invalid-money
  (testing "zero amount" (is (= :payment.validation/amount-must-be-positive
                                (:error/code (ex-data-for #(payment/new-payment (assoc valid-command :amount 0)))))))
  (testing "negative amount" (is (= :payment.validation/amount-must-be-positive
                                    (:error/code (ex-data-for #(payment/new-payment (assoc valid-command :amount -1)))))))
  (testing "non-integer amount" (is (= :payment.validation/amount-must-be-integer
                                       (:error/code (ex-data-for #(payment/new-payment (assoc valid-command :amount 12.5)))))))
  (testing "unsupported currency" (is (= :payment.validation/unsupported-currency
                                         (:error/code (ex-data-for #(payment/new-payment (assoc valid-command :currency :BTC))))))))

(deftest allows-defined-transitions-and-records-event
  (let [processing (payment/transition (payment/new-payment valid-command) :payment.status/processing occurred-at)
        paid (payment/transition processing :payment.status/paid occurred-at)]
    (is (= :payment.status/paid (:payment/status paid)))
    (is (= :payment/paid (-> paid :payment/events last :event/type)))))

(deftest rejects-undefined-transitions-with-explicit-context
  (let [refunded (-> (payment/new-payment valid-command)
                     (payment/transition :payment.status/processing)
                     (payment/transition :payment.status/paid)
                     (payment/transition :payment.status/refunded))
        error (ex-data-for #(payment/transition refunded :payment.status/processing))]
    (is (= :payment.transition/not-allowed (:error/code error)))
    (is (= :payment.status/refunded (:from-status error)))
    (is (= :payment.status/processing (:to-status error)))))

(deftest created-payments-always-have-positive-amount-and-created-status
  (let [property (check/quick-check 100
                                    (prop/for-all [amount (gen/choose 1 1000000)]
                                      (let [created (payment/new-payment (assoc valid-command :amount amount))]
                                        (and (pos? (:payment/amount created))
                                             (= :payment.status/created (:payment/status created))))))]
    (is (:pass? property) (pr-str property))))

(deftest refunded-payments-never-return-to-processing-through-allowed-transitions
  (let [property (check/quick-check 100
                                    (prop/for-all [attempts (gen/vector (gen/elements (vec payment/payment-statuses)))]
                                      (let [refunded (-> (payment/new-payment valid-command)
                                                         (payment/transition :payment.status/processing)
                                                         (payment/transition :payment.status/paid)
                                                         (payment/transition :payment.status/refunded))
                                            final-payment (reduce (fn [current status]
                                                                    (if (payment/transition-allowed? current status)
                                                                      (payment/transition current status)
                                                                      current))
                                                                  refunded attempts)]
                                        (= :payment.status/refunded (:payment/status final-payment)))))]
    (is (:pass? property) (pr-str property))))
