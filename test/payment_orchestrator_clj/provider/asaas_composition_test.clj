(ns payment-orchestrator-clj.provider.asaas-composition-test
  (:require [clojure.test :refer [deftest is]]
            [datomic.client.api :as d]
            [payment-orchestrator-clj.api.server :as server]
            [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.payment.datomic-repository :as datomic-repository]
            [payment-orchestrator-clj.payment.repository :as payment-repository]
            [payment-orchestrator-clj.payment.service :as service]
            [payment-orchestrator-clj.provider.asaas.adapter :as asaas]
            [payment-orchestrator-clj.provider.asaas.errors :as errors]
            [payment-orchestrator-clj.provider.port :as port]
            [payment-orchestrator-clj.provider.routing :as routing])
  (:import [java.time Instant]
           [java.util UUID]))

(defn- pending-payment [_]
  {:status 200 :body {:id "pay_asaas_123" :status "PENDING"}})

(deftest asaas-base-url-is-configurable-with-a-sandbox-default
  (let [environment {"ASAAS_API_KEY" "test-key" "ASAAS_BASE_URL" "https://asaas.example/v3"}
        overridden (asaas/new-gateway {:environment environment
                                       :base-url-env "ASAAS_BASE_URL" :sandbox? true
                                       :sandbox-base-url "https://sandbox.example/v3"})
        sandboxed (asaas/new-gateway {:environment {"ASAAS_API_KEY" "test-key"}
                                      :sandbox? true :sandbox-base-url "https://sandbox.example/v3"})]
    (is (= "https://asaas.example/v3" (get-in overridden [:client :base-url])))
    (is (= "https://sandbox.example/v3" (get-in sandboxed [:client :base-url])))))

(deftest asaas-is-composed-and-selected-through-the-existing-routing-boundary
  (let [catalog (#'server/gateway-catalog
                 {:default-provider :asaas
                  :routing {:default-provider :asaas}
                  :fake {:enabled false}
                  :stripe {:enabled false}
                  :asaas {:enabled true :sandbox? true
                          :sandbox-base-url "https://sandbox.example/v3"
                          :customer-id "cus_123" :due-date "2030-01-02"
                          :request-handler pending-payment}})
        selected (routing/select-provider {:currency :BRL :method :payment.method/card
                                            :routing (:routing catalog)}
                                           (:providers catalog))]
    (is (= :asaas (:provider selected)))
    (is (= #{:payment/create :payment/fetch :payment/refund :payment/cancel
             :method/card :method/pix :method/boleto}
           (:capabilities selected)))
    (is (= :asaas (:provider (port/create-payment! (:gateway selected)
                                                  {:operation/id (UUID/randomUUID) :payment/id (UUID/randomUUID)
                                                   :amount 100 :currency :BRL :method :payment.method/card
                                                   :customer {:reference "cus_123"}}))))))

(deftest asaas-result-persists-a-canonical-reference-and-idempotency-prevents-a-second-call
  (support/with-test-database
   (fn [connection]
     (let [calls (atom 0)
           gateway (asaas/new-gateway {:sandbox? true :sandbox-base-url "https://sandbox.example/v3"
                                       :due-date "2030-01-02"
                                       :request-handler #(do (swap! calls inc) (pending-payment %))})
           payments (datomic-repository/new-repository connection)
           dependencies {:payments payments :gateway gateway
                         :clock #(Instant/parse "2026-09-01T12:00:00Z")
                         :id-generator #(UUID/fromString "2cbb1e9d-2ca2-486c-bba3-bf497aae6339")}
           command {:customer-id "cus_123" :amount 12990 :currency :BRL :method :payment.method/card}
           created (service/create-payment-idempotently! dependencies command "asaas-idempotency-key" {:source :source/test})
           replayed (service/create-payment-idempotently! dependencies command "asaas-idempotency-key" {:source :source/test})
           payment-id (get-in created [:payment :payment/id])]
       (is (= :created (:outcome created)))
       (is (= :replayed (:outcome replayed)))
       (is (= 1 @calls))
       (is (= :payment.status/processing (get-in created [:payment :payment/status])))
       (is (= #{[:asaas "pay_asaas_123" :provider.status/processing]}
              (set (d/q '[:find ?provider ?reference ?status
                          :in $ ?payment-id
                          :where
                          [?payment :payment/id ?payment-id]
                          [?provider-payment :provider-payment/payment ?payment]
                          [?provider-payment :provider-payment/provider ?provider]
                          [?provider-payment :provider-payment/reference ?reference]
                          [?provider-payment :provider-payment/status ?status]]
                        (d/db connection) payment-id))))))))

(deftest asaas-timeout-keeps-the-payment-processing-for-reconciliation
  (support/with-test-database
   (fn [connection]
     (let [payments (datomic-repository/new-repository connection)
           gateway (asaas/new-gateway {:sandbox? true :sandbox-base-url "https://sandbox.example/v3"
                                       :due-date "2030-01-02"
                                       :request-handler (fn [_] (throw (errors/timeout-error)))})
           dependencies {:payments payments :gateway gateway
                         :clock #(Instant/parse "2026-09-01T12:00:00Z")
                         :id-generator #(UUID/fromString "9e91f5ef-a1f6-4595-b452-ea6a3cee2201")}
           error (try
                   (service/create-payment-idempotently! dependencies
                                                         {:customer-id "cus_123" :amount 100 :currency :BRL :method :payment.method/card}
                                                         "asaas-timeout-key" {:source :source/test})
                   nil
                   (catch clojure.lang.ExceptionInfo exception exception))
           stored (payment-repository/find-payment payments #uuid "9e91f5ef-a1f6-4595-b452-ea6a3cee2201")]
       (is (= :provider.error/timeout (:provider/error (ex-data error))))
       (is (false? (:outcome-known? (ex-data error))))
       (is (= :payment.status/processing (:payment/status stored)))))))
