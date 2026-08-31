(ns payment-orchestrator-clj.provider.stripe-mapper-test
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.provider.stripe.mapper :as mapper]))

(defn- fixture [name]
  (edn/read-string (slurp (io/resource (str "fixtures/stripe/" name ".edn")))))

(deftest create-request-uses-an-operation-scoped-idempotency-key
  (let [operation-id #uuid "0c8b8d05-09a0-44c4-bd44-f831ac8958ce"
        request (mapper/create-request {:operation/id operation-id
                                        :payment/id #uuid "9c8b8d05-09a0-44c4-bd44-f831ac8958ce"
                                        :amount 12990 :currency :BRL}
                                       "pm_card_visa")]
    (is (= "payment-orchestrator-clj:create-payment:0c8b8d05-09a0-44c4-bd44-f831ac8958ce"
           (:idempotency-key request)))
    (is (= {"amount" "12990" "currency" "brl" "confirm" "true"
            "payment_method" "pm_card_visa"
            "payment_method_types[]" "card"
            "metadata[payment_id]" "9c8b8d05-09a0-44c4-bd44-f831ac8958ce"}
           (:form request)))))

(deftest payment-intent-statuses-map-to-canonical-results
  (let [succeeded (mapper/payment-intent->provider-result (fixture "payment-intent-succeeded"))
        action (mapper/payment-intent->provider-result (fixture "payment-intent-requires-action"))]
    (is (= :stripe (:provider succeeded)))
    (is (= :provider.status/succeeded (:provider-payment/status succeeded)))
    (is (= :provider.status/requires-action (:provider-payment/status action)))
    (is (= :client-secret (get-in action [:provider-payment/action :action/type])))))
