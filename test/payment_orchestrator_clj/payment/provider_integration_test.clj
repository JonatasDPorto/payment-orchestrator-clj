(ns payment-orchestrator-clj.payment.provider-integration-test
  (:require [clojure.test :refer [deftest is]]
            [datomic.client.api :as d]
            [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.payment.datomic-repository :as datomic-repository]
            [payment-orchestrator-clj.payment.repository :as repository]
            [payment-orchestrator-clj.payment.service :as service]
            [payment-orchestrator-clj.provider.fake :as fake])
  (:import [java.time Instant]
           [java.util UUID]))

(deftest provider-result-is-persisted
  (support/with-test-database
   (fn [connection]
     (let [deps {:payments (datomic-repository/new-repository connection)
                 :gateway (fake/new-gateway {:mode :always-success})
                 :clock #(Instant/parse "2026-08-30T12:00:00Z")
                 :id-generator #(UUID/randomUUID)}
           result (service/create-payment-idempotently!
                   deps {:customer-id "customer-123" :amount 12990 :currency :BRL
                         :method :payment.method/card}
                   "provider-key" {:source :source/test})
           payment-id (get-in result [:payment :payment/id])]
       (is (= :created (:outcome result)))
       (is (= :payment.status/processing (get-in result [:payment :payment/status])))
       (is (some? (get-in result [:provider-result :provider-payment/reference])))
       (is (= [[1]]
              (d/q '[:find (count ?provider-payment)
                     :in $ ?payment-id
                     :where
                     [?provider-payment :provider-payment/payment ?payment]
                     [?payment :payment/id ?payment-id]]
                   (d/db connection) payment-id)))))))

(deftest routed-provider-is-recorded-and-an-unavailable-route-does-not-create-a-payment
  (support/with-test-database
   (fn [connection]
     (let [payments (datomic-repository/new-repository connection)
           metrics (atom {})
           command {:customer-id "customer-123" :merchant-id "merchant-a" :amount 12990 :currency :BRL
                    :method :payment.method/card}
           dependencies {:payments payments
                         :providers [{:provider :fake
                                      :gateway (fake/new-gateway {:mode :always-success})
                                      :available? true
                                      :capabilities #{:payment/create :method/card}}]
                         :routing {:by-merchant {"merchant-a" :fake} :default-provider :fake}
                         :metrics metrics
                         :clock #(Instant/parse "2026-08-30T12:00:00Z")
                         :id-generator #(UUID/randomUUID)}
           result (service/create-payment-idempotently! dependencies command "routed-provider-key" {:source :source/test})
           unavailable-dependencies (assoc dependencies :providers
                                           [{:provider :fake :available? false
                                             :gateway (fake/new-gateway {:mode :always-success})
                                             :capabilities #{:payment/create :method/card}}])]
       (is (= :created (:outcome result)))
       (is (= #{:fake}
              (set (map first (d/q '[:find ?provider
                                     :where [_ :provider-payment/provider ?provider]]
                                   (d/db connection))))))
       (try
         (service/create-payment-idempotently! unavailable-dependencies command "unavailable-route-key" {:source :source/test})
         (is false "expected unavailable route")
         (catch clojure.lang.ExceptionInfo error
           (is (= :provider.error/unavailable (:provider/error (ex-data error))))))
       (is (= 1 (get @metrics "provider_routing_errors_total")))
       (is (= [[1]]
              (d/q '[:find (count ?payment)
                     :where [?payment :payment/id]]
                   (d/db connection))))))))

(deftest pix-provider-result-persists-only-the-canonical-action
  (support/with-test-database
   (fn [connection]
     (let [payments (datomic-repository/new-repository connection)
           dependencies {:payments payments
                         :gateway (fake/new-gateway {:mode :always-success})
                         :clock #(Instant/parse "2026-08-30T12:00:00Z")
                         :id-generator #(UUID/randomUUID)}
           result (service/create-payment-idempotently!
                   dependencies {:customer-id "customer-123" :amount 12990 :currency :BRL
                                 :method :payment.method/pix}
                   "pix-provider-key" {:source :source/test})
           payment-id (get-in result [:payment :payment/id])
           stored (repository/find-payment payments payment-id)]
       (is (= :payment.status/requires-action (:payment/status stored)))
       (is (= :pix/qr-code (get-in stored [:payment/action :payment-action/type])))
       (is (= "2030-01-01T00:00:00Z"
              (str (get-in stored [:payment/action :payment-action/expires-at]))))))))
