(ns payment-orchestrator-clj.provider.routing-test
  (:require [clojure.test :refer [deftest is testing]]
            [payment-orchestrator-clj.provider.routing :as routing]))

(def providers
  [{:provider :fake :available? true :cost 30
    :capabilities #{:payment/create :method/card}
    :currencies #{:BRL}}
   {:provider :stripe :available? true :cost 45
    :capabilities #{:payment/create :method/card}
    :currencies #{:BRL :USD}}])

(def base-context
  {:merchant-id "merchant-a" :currency :BRL :method :payment.method/card
   :routing {:default-provider :fake}})

(deftest routing-prioritizes-merchant-currency-method-and-default
  (testing "merchant has the highest precedence"
    (is (= :stripe (:provider (routing/select-provider
                                (assoc base-context :routing {:default-provider :fake
                                                              :by-payment-method {:payment.method/card :fake}
                                                              :by-currency {:BRL :fake}
                                                              :by-merchant {"merchant-a" :stripe}})
                                providers)))))
  (testing "currency is selected before payment method"
    (is (= :stripe (:provider (routing/select-provider
                                (assoc base-context :routing {:default-provider :fake
                                                              :by-payment-method {:payment.method/card :fake}
                                                              :by-currency {:BRL :stripe}})
                                providers)))))
  (testing "payment method is selected before default"
    (is (= :stripe (:provider (routing/select-provider
                                (assoc base-context :routing {:default-provider :fake
                                                              :by-payment-method {:payment.method/card :stripe}})
                                providers)))))
  (is (= :fake (:provider (routing/select-provider base-context providers)))))

(deftest routing-can-select-lowest-compatible-cost
  (is (= :fake (:provider (routing/select-provider
                           (assoc base-context :routing {:strategy :routing.strategy/lowest-cost})
                           providers)))))

(deftest routing-rejects-unavailable-or-incompatible-configured-provider-without-fallback
  (doseq [configured-providers [(assoc-in providers [1 :available?] false)
                               (assoc-in providers [1 :currencies] #{:USD})]]
    (try
      (routing/select-provider (assoc base-context :routing {:default-provider :stripe}) configured-providers)
      (is false "expected the configured provider to be rejected")
      (catch clojure.lang.ExceptionInfo error
        (is (= :provider.error/unavailable (:provider/error (ex-data error))))
        (is (= :routing/no-available-provider (:routing/error (ex-data error))))))))
