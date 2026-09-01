(ns payment-orchestrator-clj.payment.service
  "Application orchestration for payment commands."
  (:require [payment-orchestrator-clj.payment.domain :as domain]
            [payment-orchestrator-clj.payment.idempotency :as idempotency]
            [payment-orchestrator-clj.payment.repository :as repository]
            [payment-orchestrator-clj.ledger.service :as ledger]
            [payment-orchestrator-clj.reconciliation.repository :as operations]
            [payment-orchestrator-clj.observability.metrics :as metrics]
            [payment-orchestrator-clj.observability.trace :as trace]
            [payment-orchestrator-clj.consumer-webhook.service :as consumer-webhook]
            [payment-orchestrator-clj.merchant.provider-runtime :as merchant-runtime]
            [payment-orchestrator-clj.provider.port :as provider]
            [payment-orchestrator-clj.provider.routing :as routing]))

(defn create-payment!
  "Creates and persists a payment using injected time and ID generators."
  ([dependencies command]
   (create-payment! dependencies command {:source :source/application}))
  ([{:keys [payments clock id-generator]} command transaction-context]
   (let [payment (domain/new-payment (assoc command :id (id-generator) :occurred-at (clock)))]
     (repository/save-payment! payments payment transaction-context)
     payment)))

(defn- to-processing [payment occurred-at]
  (if (= :payment.status/created (:payment/status payment))
    (domain/transition payment :payment.status/processing occurred-at)
    payment))

(defn- apply-provider-result [payment provider-result occurred-at]
  (let [updated (case (:provider-payment/status provider-result)
                  :provider.status/processing (to-processing payment occurred-at)
                  :provider.status/requires-action (domain/transition (to-processing payment occurred-at)
                                                                      :payment.status/requires-action occurred-at)
                  :provider.status/succeeded (domain/transition (to-processing payment occurred-at)
                                                                :payment.status/paid occurred-at)
                  :provider.status/failed (domain/transition (to-processing payment occurred-at)
                                                             :payment.status/failed occurred-at)
                  :provider.status/cancelled (domain/transition (to-processing payment occurred-at)
                                                                :payment.status/cancelled occurred-at)
                  (throw (provider/provider-error :provider.error/unexpected-response
                                                  {:provider (:provider provider-result)
                                                   :retryable? false :outcome-known? true})))
        action (:provider-payment/action provider-result)]
    (if (contains? #{:pix/qr-code :boleto/voucher} (:action/type action))
      (assoc updated :payment/action
             (into {} (remove (comp nil? val)
                              {:payment-action/type (:action/type action)
                               :payment-action/payload (:action/payload action)
                               :payment-action/qr-code-url (:action/qr-code-url action)
                               :payment-action/hosted-instructions-url (:action/hosted-instructions-url action)
                               :payment-action/document-url (:action/document-url action)
                               :payment-action/expires-at (:action/expires-at action)})) )
      updated)))

(defn- provider-command [payment idempotency-key command]
  {:operation/id (java.util.UUID/randomUUID)
   :payment/id (:payment/id payment)
   :amount (:payment/amount payment)
   :currency (:payment/currency payment)
   :method (:payment/method payment)
   :customer {:reference (:payment/customer-id payment)}
   :pix (:pix command)
   :boleto (:boleto command)
   :idempotency-key idempotency-key})

(defn- operation [payment command now provider-id]
  {:provider-operation/id (:operation/id command)
   :provider-operation/payment (:payment/id payment)
   :provider-operation/provider provider-id
   :provider-operation/type :provider-operation.type/create
   :provider-operation/idempotency-key (:idempotency-key command)
   :provider-operation/status :provider-operation.status/started
   :provider-operation/started-at now})

(defn- gateway-for
  "Uses routing when a provider catalog is supplied, while retaining the injected
  single gateway boundary used by existing tests and non-routed compositions."
  [{:keys [gateway providers routing merchant-provider-runtime provider-catalog]} payment]
  (if merchant-provider-runtime
    (let [merchant-context {:merchant-id (:payment/merchant-id payment)}
          candidates (merchant-runtime/provider-candidates merchant-provider-runtime merchant-context provider-catalog)
          selected (routing/select-provider {:merchant-id (:payment/merchant-id payment)
                                             :currency (:payment/currency payment)
                                             :method (:payment/method payment)
                                             :routing routing}
                                            candidates)]
      (assoc selected :gateway (merchant-runtime/create-gateway merchant-provider-runtime
                                                                  merchant-context
                                                                  (:provider selected))))
    (if (seq providers)
    (routing/select-provider {:merchant-id (:payment/merchant-id payment)
                              :currency (:payment/currency payment)
                              :method (:payment/method payment)
                              :routing routing}
                             providers)
    {:provider :provider/unknown :gateway gateway})))

(defn- routed-gateway-for [dependencies payment]
  (try
    (gateway-for dependencies payment)
    (catch clojure.lang.ExceptionInfo error
      (when (and (:metrics dependencies) (:provider/error? (ex-data error)))
        (metrics/inc! (:metrics dependencies) "provider_routing_errors_total"))
      (throw error))))

(defn create-payment-idempotently!
  "Creates a payment once per consumer key and returns :created, :replayed or :conflict."
  [{:keys [payments clock id-generator tracer] :as dependencies} command idempotency-key transaction-context]
  (let [now (clock)
        payment (assoc (domain/new-payment (assoc command :id (id-generator) :occurred-at now))
                       :payment/merchant-id (or (:merchant-id command) "default"))
        gateway-selection (routed-gateway-for dependencies payment)
        {:keys [provider gateway]} gateway-selection
        record {:idempotency/key idempotency-key
                :idempotency/request-hash (idempotency/request-hash command)
                :idempotency/payment-id (:payment/id payment)
                :idempotency/created-at now}
        trace-context (trace/enrich-context (or (:observability/context transaction-context)
                                                (trace/root-context (:request-id transaction-context) (:correlation-id transaction-context) nil))
                                           {:payment-id (:payment/id payment) :merchant-id (:payment/merchant-id payment) :provider provider})
        local-result (repository/create-payment-idempotently! payments payment record transaction-context)]
    (if (not= :created (:outcome local-result))
      local-result
      (trace/with-span (or tracer (trace/new-tracer)) trace-context "payment.create" {}
       (fn [payment-context]
        (let [operation-command (provider-command payment idempotency-key command)]
        (when-let [registry (:metrics dependencies)] (metrics/inc! registry "payment_create_total"))
        (try
          (let [_ (when-let [operation-repository (:operations dependencies)]
                  (operations/start-operation! operation-repository (operation payment operation-command now provider)
                                             (assoc transaction-context :event-type :event/provider-operation-started)))
              provider-started (System/nanoTime)
              raw-provider-result (trace/with-span (or tracer (trace/new-tracer)) payment-context "provider.create"
                                                    {:provider provider :operation :create}
                                                    (fn [_] (provider/create-payment! gateway operation-command)))
              _ (when-let [registry (:metrics dependencies)]
                  (metrics/observe! registry "provider_request_duration_seconds"
                                    (/ (- (System/nanoTime) provider-started) 1.0e9)))
              provider-result (cond-> (assoc raw-provider-result :provider-payment/created-at now)
                                (:merchant-provider/account-id gateway-selection)
                                (assoc :provider-account/id (:merchant-provider/account-id gateway-selection)))
              updated (apply-provider-result payment provider-result now)]
          (let [provider-context (assoc transaction-context :reason :reason/provider-result
                                        :event-type :event/payment-provider-result)
                 stored (trace/with-span (or tracer (trace/new-tracer)) payment-context "datomic.transact" {}
                          (fn [_] (repository/record-provider-result! payments updated provider-result provider-context)))]
             (when (not= :payment.status/created (:payment/status stored))
               (when-let [registry (:metrics dependencies)]
                 (metrics/inc! registry "payment_status_transition_total")))
            (when-let [operation-repository (:operations dependencies)]
              (operations/complete-operation! operation-repository (:operation/id operation-command)
                                    {:provider-operation/status :provider-operation.status/succeeded
                                     :provider-operation/provider (:provider provider-result)
                                     :provider-operation/provider-reference (:provider-payment/reference provider-result)
                                     :provider-operation/completed-at now} provider-context))
            (when (and (:consumer-deliveries dependencies) (seq (:consumer-webhook-endpoints dependencies)))
              (consumer-webhook/publish-payment-event!
               {:repository (:consumer-deliveries dependencies) :endpoints (:consumer-webhook-endpoints dependencies)
                :id-generator id-generator :clock clock}
               {:event/id (str (:payment/id stored)) :event/type (str "payment." (name (:payment/status stored)))
                :event/version 1 :event/aggregate-id (str (:payment/id stored)) :event/occurred-at (str now)}))
            (ledger/record-payment-settlement! {:ledger (:ledger dependencies) :clock clock :id-generator id-generator}
                                               stored provider-context)
            {:outcome :created :payment stored :provider-result provider-result}))
        (catch clojure.lang.ExceptionInfo error
          (let [data (ex-data error)
                operation-repository (:operations dependencies)]
            (when-let [registry (:metrics dependencies)]
              (metrics/inc! registry "provider_request_errors_total")
              (when (= :provider.error/timeout (:provider/error data)) (metrics/inc! registry "provider_timeout_total")))
            (when operation-repository
              (operations/complete-operation! operation-repository (:operation/id operation-command)
                                    {:provider-operation/status (if (:outcome-known? data)
                                                                  :provider-operation.status/failed
                                                                  :provider-operation.status/outcome-unknown)
                                     :provider-operation/provider (:provider data)
                                     :provider-operation/provider-reference (:provider-reference data)
                                     :provider-operation/error-category (:provider/error data)
                                     :provider-operation/completed-at now}
                                    (assoc transaction-context :reason :reason/provider-error
                                                               :event-type :event/provider-operation-ambiguous)))
            (cond
              (and (:provider/error? data) (:outcome-known? data))
              (let [failed (domain/transition (to-processing payment now) :payment.status/failed now)]
                (repository/save-payment! payments failed (assoc transaction-context :reason :reason/provider-error
                                                                  :event-type :event/payment-provider-error)))
              (and (:provider/error? data) (not (:outcome-known? data)))
              (repository/save-payment! payments (to-processing payment now)
                                        (assoc transaction-context :reason :reason/provider-outcome-unknown
                                                                   :event-type :event/payment-reconciliation-required))))
          (throw error)))))))))
