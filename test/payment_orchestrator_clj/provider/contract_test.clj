(ns payment-orchestrator-clj.provider.contract-test
  (:require [clojure.test :refer [deftest is testing]]
            [payment-orchestrator-clj.provider.fake :as fake]
            [payment-orchestrator-clj.provider.port :as port]))

(defn run-contract [gateway]
  (let [command {:payment/id (java.util.UUID/randomUUID)
                 :amount 1000 :currency "BRL" :method :card}]
    (testing "create returns canonical reference and status"
      (let [created (port/create-payment! gateway command)]
        (is (some? (:provider-payment/reference created)))
        (is (contains? port/provider-statuses (:provider-payment/status created)))
        (is (= created (port/fetch-payment gateway (:provider-payment/reference created))))))
    (testing "cancel and refund return canonical statuses"
      (is (= :provider.status/cancelled
             (:provider-payment/status (port/cancel-payment! gateway command))))
      (is (= :provider.status/succeeded
             (:provider-payment/status (port/refund-payment! gateway command)))))))

(deftest fake-success-contract
  (run-contract (fake/new-gateway {:mode :always-success})))

(deftest fake-action-contract
  (run-contract (fake/new-gateway {:mode :requires-action})))

(deftest fake-errors-are-canonical
  (doseq [[mode category known?] [[:always-fail :provider.error/declined true]
                                  [:timeout :provider.error/timeout false]]]
    (testing (str mode)
      (try
        (port/create-payment! (fake/new-gateway {:mode mode}) {:payment/id (java.util.UUID/randomUUID)})
        (is false "expected provider error")
        (catch clojure.lang.ExceptionInfo error
          (is (= category (:provider/error (ex-data error))))
          (is (= known? (:outcome-known? (ex-data error)))))))))
