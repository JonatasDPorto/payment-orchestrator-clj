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
            [payment-orchestrator-clj.ledger.datomic-repository :as ledger-repository]
            [payment-orchestrator-clj.ledger.repository :as ledger]
            [payment-orchestrator-clj.provider.fake :as fake]
            [payment-orchestrator-clj.provider.stripe.adapter :as stripe]
            [payment-orchestrator-clj.webhook.datomic-repository :as webhook-repository]
            [payment-orchestrator-clj.webhook.service :as webhook-service])
  (:import [java.time Instant]
           [java.util UUID]))

(defn- normalize-client-config [database-config]
  (update (dissoc database-config :database-name) :storage-dir
          #(if (string? %) (.getAbsolutePath (io/file %)) %)))

(defn- selected-provider [gateway-config]
  (keyword (or (System/getenv "PAYMENT_ORCHESTRATOR_DEFAULT_PROVIDER")
               (name (:default-provider gateway-config)))))

(defn application-dependencies [application-config]
  (let [database-config (:database application-config)
        client (datomic-client/new-client (normalize-client-config database-config))
        connection (datomic-client/create-connection! client (:database-name database-config))
        gateway-config (:payments application-config)
        provider (selected-provider gateway-config)
        gateway (case provider
                  :fake (fake/new-gateway (:fake gateway-config))
                  :stripe (stripe/new-gateway (assoc (:stripe gateway-config)
                                                     :environment (System/getenv)))
                  (throw (ex-info "Unsupported payment provider"
                                  {:provider provider})))]
    (schema/install! connection)
    (let [ledger-repository (ledger-repository/new-repository connection)]
      (ledger/ensure-accounts! ledger-repository)
      {:payments (datomic-repository/new-repository connection)
     :audit (audit-repository/new-repository connection)
     :provider-events (webhook-repository/new-repository connection)
     :ledger ledger-repository
     :gateway gateway
     :clock #(Instant/now)
     :id-generator #(UUID/randomUUID)
     :stripe-webhook-secret (System/getenv "STRIPE_WEBHOOK_SECRET")
     :dispatcher webhook-service/dispatch!})))

(defn start! []
  (let [application-config (config/base-config)
        dependencies (application-dependencies application-config)]
    (jetty/run-jetty (routes/handler dependencies)
                     {:port (get-in application-config [:http :port]) :join? true})))
