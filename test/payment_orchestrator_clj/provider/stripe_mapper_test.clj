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
                                        :amount 12990 :currency :BRL :method :payment.method/card}
                                       "pm_card_visa")]
    (is (= "payment-orchestrator-clj:create-payment:0c8b8d05-09a0-44c4-bd44-f831ac8958ce"
           (:idempotency-key request)))
    (is (= {"amount" "12990" "currency" "brl" "confirm" "true"
            "payment_method" "pm_card_visa"
            "payment_method_types[]" "card"
            "metadata[payment_id]" "9c8b8d05-09a0-44c4-bd44-f831ac8958ce"}
           (:form request)))))

(deftest pix-create-request-and-action-are-canonical
  (let [request (mapper/create-request {:operation/id #uuid "0c8b8d05-09a0-44c4-bd44-f831ac8958ce"
                                        :payment/id #uuid "9c8b8d05-09a0-44c4-bd44-f831ac8958ce"
                                        :amount 12990 :currency :BRL :method :payment.method/pix
                                        :pix {:tax-id "000.000.000-00" :email "succeed_immediately@example.com" :name "Stripe Test"}}
                                       "unused-card-method")
        result (mapper/payment-intent->provider-result (fixture "payment-intent-pix-requires-action"))]
    (is (= "pix" (get-in request [:form "payment_method_types[]"])))
    (is (= "pix" (get-in request [:form "payment_method_data[type]"])))
    (is (= "000.000.000-00" (get-in request [:form "payment_method_data[billing_details][tax_id]"])))
    (is (nil? (get-in request [:form "payment_method"])))
    (is (= :provider.status/requires-action (:provider-payment/status result)))
    (is (= {:action/type :pix/qr-code
            :action/payload "000201PIXTESTPAYLOAD6304ABCD"
            :action/qr-code-url "https://stripe.example/pix.png"
            :action/hosted-instructions-url "https://stripe.example/pix-instructions"
            :action/expires-at (java.time.Instant/parse "2030-01-01T00:00:00Z")}
           (:provider-payment/action result)))))


(deftest malformed-pix-action-is-a-canonical-provider-error
  (try
    (mapper/payment-intent->provider-result
     {:request-id "req_bad_pix"
      :body {:id "pi_bad_pix" :status "requires_action"
             :next_action {:type "pix_display_qr_code" :pix_display_qr_code {}}}})
    (is false "expected a malformed Pix action to be rejected")
    (catch clojure.lang.ExceptionInfo error
      (is (= :provider.error/unexpected-response (:provider/error (ex-data error)))))))

(deftest boleto-create-request-and-voucher-action-are-canonical
  (let [request (mapper/create-request {:operation/id #uuid "0c8b8d05-09a0-44c4-bd44-f831ac8958ce"
                                        :payment/id #uuid "9c8b8d05-09a0-44c4-bd44-f831ac8958ce"
                                        :amount 100 :currency :BRL :method :payment.method/boleto
                                        :boleto {:tax-id "000.000.000-00" :email "succeed_immediately@example.com" :name "Stripe Test"
                                                 :address {:line1 "1234 Av Paulista" :city "Sao Paulo" :state "SP"
                                                           :postal-code "01310-000" :country "BR"}}}
                                       nil)
        result (mapper/payment-intent->provider-result (fixture "payment-intent-boleto-requires-action"))]
    (is (= "boleto" (get-in request [:form "payment_method_types[]"])))
    (is (= "boleto" (get-in request [:form "payment_method_data[type]"])))
    (is (= "000.000.000-00" (get-in request [:form "payment_method_data[boleto][tax_id]"])))
    (is (= "BR" (get-in request [:form "payment_method_data[billing_details][address][country]"])))
    (is (= :provider.status/requires-action (:provider-payment/status result)))
    (is (= {:action/type :boleto/voucher
            :action/payload "00190500954014481606906809350314337370000000100"
            :action/hosted-instructions-url "https://stripe.example/boleto/voucher"
            :action/document-url "https://stripe.example/boleto/voucher.pdf"
            :action/expires-at (java.time.Instant/parse "2030-01-03T23:59:59Z")}
           (:provider-payment/action result)))))

(deftest pix-test-scenario-emails-and-refunds-use-canonical-stripe-requests
  (doseq [email ["succeed_immediately@example.com"
                 "expire_immediately@example.com"
                 "expire_with_delay@example.com"
                 "fill_never@example.com"]]
    (is (= email
           (get-in (mapper/create-request {:operation/id #uuid "0c8b8d05-09a0-44c4-bd44-f831ac8958ce"
                                           :payment/id #uuid "9c8b8d05-09a0-44c4-bd44-f831ac8958ce"
                                           :amount 100 :currency :BRL :method :payment.method/pix
                                           :pix {:tax-id "000.000.000-00" :email email :name "Stripe Test"}}
                                          nil)
                    [:form "payment_method_data[billing_details][email]"]))))
  (is (= {"payment_intent" "pi_pix_refund"}
         (:form (mapper/refund-request {:operation/id #uuid "0c8b8d05-09a0-44c4-bd44-f831ac8958ce"
                                        :provider-payment/reference "pi_pix_refund"})))))

(deftest payment-intent-statuses-map-to-canonical-results
  (let [succeeded (mapper/payment-intent->provider-result (fixture "payment-intent-succeeded"))
        action (mapper/payment-intent->provider-result (fixture "payment-intent-requires-action"))]
    (is (= :stripe (:provider succeeded)))
    (is (= :provider.status/succeeded (:provider-payment/status succeeded)))
    (is (= :provider.status/requires-action (:provider-payment/status action)))
    (is (= :client-secret (get-in action [:provider-payment/action :action/type])))))
