(ns payment-orchestrator-clj.api.server
  "Runtime composition for the HTTP API."
  (:require [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [payment-orchestrator-clj.api.routes :as routes]
            [payment-orchestrator-clj.config :as config]
            [payment-orchestrator-clj.datomic.client :as datomic-client]
            [payment-orchestrator-clj.datomic.schema :as schema]
            [payment-orchestrator-clj.payment.datomic-repository :as datomic-repository]
            [payment-orchestrator-clj.audit.datomic-repository :as audit-repository]
            [payment-orchestrator-clj.reconciliation.datomic-repository :as reconciliation-repository]
            [payment-orchestrator-clj.reconciliation.service :as reconciliation]
            [payment-orchestrator-clj.ledger.datomic-repository :as ledger-repository]
            [payment-orchestrator-clj.ledger.repository :as ledger]
            [payment-orchestrator-clj.provider.fake :as fake]
            [payment-orchestrator-clj.provider.stripe.adapter :as stripe]
            [payment-orchestrator-clj.webhook.datomic-repository :as webhook-repository]
            [payment-orchestrator-clj.webhook.service :as webhook-service]
            [payment-orchestrator-clj.observability.metrics :as metrics]
            [payment-orchestrator-clj.security :as security])
  (:import [java.time Instant]
           [java.util UUID]))

(defn- normalize-client-config [database-config]
  (update (dissoc database-config :database-name) :storage-dir
          #(if (string? %) (.getAbsolutePath (io/file %)) %)))

(defn- configured-provider [gateway-config]
  (keyword (or (System/getenv "PAYMENT_ORCHESTRATOR_DEFAULT_PROVIDER")
               (name (:default-provider gateway-config)))))

(defn- gateway-catalog [gateway-config]
  (let [default-provider (configured-provider gateway-config)
        routing-config (assoc (:routing gateway-config)
                              :default-provider default-provider)
        fake-config (:fake gateway-config)
        stripe-config (:stripe gateway-config)]
    {:routing routing-config
     :providers (cond-> []
                  (or (:enabled fake-config) (= :fake default-provider))
                  (conj {:provider :fake
                         :gateway (fake/new-gateway fake-config)
                         :capabilities #{:payment/create :payment/fetch :payment/refund :payment/cancel :method/card :method/pix :method/boleto}
                         :cost (:cost fake-config 0)})
                  (or (:enabled stripe-config) (= :stripe default-provider))
                  (conj {:provider :stripe
                         :gateway (stripe/new-gateway (assoc stripe-config :environment (System/getenv)))
                         :capabilities #{:payment/create :payment/fetch :payment/refund :payment/cancel :method/card :method/pix :method/boleto}
                         :cost (:cost stripe-config 0)}))}))

(defn application-dependencies [application-config]
  (let [database-config (:database application-config)
        client (datomic-client/new-client (normalize-client-config database-config))
        connection (datomic-client/create-connection! client (:database-name database-config))
        gateway-config (:payments application-config)
        catalog (gateway-catalog gateway-config)]
    (schema/install! connection)
    (let [ledger-repository (ledger-repository/new-repository connection)]
      (ledger/ensure-accounts! ledger-repository)
      {:payments (datomic-repository/new-repository connection)
     :audit (audit-repository/new-repository connection)
     :operations (reconciliation-repository/new-repository connection)
     :provider-events (webhook-repository/new-repository connection)
     :ledger ledger-repository
     :providers (:providers catalog)
     :routing (:routing catalog)
     :metrics (metrics/registry)
     :clock #(Instant/now)
     :id-generator #(UUID/randomUUID)
     :stripe-webhook-secret (System/getenv "STRIPE_WEBHOOK_SECRET")
     :api-key (security/required-api-key (System/getenv "PAYMENT_ORCHESTRATOR_API_KEY"))
     :rate-limiter (security/new-rate-limiter {:limit 60 :window-ms 60000})
     :max-request-body-bytes 1048576
     :dispatcher webhook-service/dispatch!})))

(defn start! []
  (let [application-config (config/base-config)
        dependencies (application-dependencies application-config)]
    (jetty/run-jetty (routes/handler dependencies)
                     {:port (get-in application-config [:http :port]) :join? true})))
