(ns payment-orchestrator-clj.performance-runner
  (:require [clojure.data.json :as json]
            [payment-orchestrator-clj.api.routes :as routes]
            [payment-orchestrator-clj.audit.datomic-repository :as audit]
            [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.ledger.datomic-repository :as ledger-repository]
            [payment-orchestrator-clj.ledger.repository :as ledger]
            [payment-orchestrator-clj.payment.datomic-repository :as payment-repository]
            [payment-orchestrator-clj.performance :as performance]
            [payment-orchestrator-clj.provider.fake :as fake]
            [payment-orchestrator-clj.provider.port :as provider]
            [payment-orchestrator-clj.webhook.datomic-repository :as webhook-repository]
            [payment-orchestrator-clj.webhook.service :as webhook])
  (:import [java.io ByteArrayInputStream]
           [java.nio.charset StandardCharsets]
           [java.time Instant]
           [java.util UUID]))

(defn- request [method uri headers body]
  {:request-method method :uri uri :headers headers
   :body (ByteArrayInputStream. (.getBytes body StandardCharsets/UTF_8))})

(defn- elapsed [operation]
  (let [started (System/nanoTime)
        samples (operation)]
    [samples (- (System/nanoTime) started)]))

(defn- dependencies [connection]
  (let [ledger-repository (ledger-repository/new-repository connection)]
    (ledger/ensure-accounts! ledger-repository)
    {:payments (payment-repository/new-repository connection)
     :audit (audit/new-repository connection)
     :provider-events (webhook-repository/new-repository connection)
     :ledger ledger-repository
     :gateway (fake/new-gateway {:mode :always-success})
     :api-key "performance-local-key"
     :clock #(Instant/now)
     :id-generator #(UUID/randomUUID)}))

(defn- create-payment! [handler index]
  (let [payload {:customerId (str "perf-customer-" index) :amount (+ 1000 (mod index 9000))
                 :currency "BRL" :method "card"}
        response (handler (request :post "/v1/payments"
                                   {"content-type" "application/json" "x-api-key" "performance-local-key"
                                    "idempotency-key" (str "perf-payment-" index)}
                                   (json/write-str payload)))]
    (when-not (= 201 (:status response))
      (throw (ex-info "Performance write failed" {:status (:status response)})))
    (:id (json/read-str (:body response) :key-fn keyword))))

(defn- profile [operation count]
  (let [[samples elapsed-nanos] (elapsed #(mapv (fn [_] (performance/measure! operation)) (range count)))]
    (performance/summary samples elapsed-nanos)))

(defn- webhook-burst! [dependencies count]
  (let [[samples elapsed-nanos]
        (elapsed #(mapv (fn [index]
                          (performance/measure!
                           (fn []
                             (webhook/enqueue-stripe-event!
                              dependencies
                              (str "{\"id\":\"evt_perf_" index
                                   "\",\"type\":\"customer.created\",\"data\":{\"object\":{\"id\":\"cus_perf\"}}}")))))
                        (range count)))]
    (webhook/process-pending! dependencies)
    (performance/summary samples elapsed-nanos)))

(defn run-profile! [sample-size webhook-count provider-latency-ms]
  (support/with-test-database
   (fn [connection]
     (let [dependencies (dependencies connection)
           handler (routes/handler dependencies)
           ids (mapv #(create-payment! handler %) (range sample-size))
           read-profile (profile #(handler (request :get (str "/v1/payments/" (first ids))
                                                     {"x-api-key" "performance-local-key"} "")) sample-size)
           history-profile (profile #(handler (request :get (str "/v1/payments/" (first ids) "/history")
                                                        {"x-api-key" "performance-local-key"} "")) sample-size)
           write-index (atom sample-size)
           write-profile (profile #(create-payment! handler (swap! write-index inc)) sample-size)
            slow-gateway (fake/new-gateway {:mode :slow-success :latency-ms provider-latency-ms})
           provider-latency (profile #(provider/create-payment! slow-gateway {:payment/id (UUID/randomUUID)}) 1)]
       {:environment (assoc (performance/runtime-snapshot)
                            :java-version (System/getProperty "java.version")
                             :sample-size sample-size :webhook-count webhook-count
                             :provider-latency-ms provider-latency-ms)
        :read read-profile
        :history history-profile
        :write write-profile
        :webhook-burst (webhook-burst! dependencies webhook-count)
         :provider-5s-latency provider-latency}))))

(defn -main [& _]
  (let [sample-size (Long/parseLong (or (System/getenv "PERF_SAMPLE_SIZE") "100"))
        webhook-count (Long/parseLong (or (System/getenv "PERF_WEBHOOK_COUNT") "200"))
        provider-latency-ms (Long/parseLong (or (System/getenv "PERF_PROVIDER_LATENCY_MS") "100"))]
    (prn (run-profile! sample-size webhook-count provider-latency-ms))))
