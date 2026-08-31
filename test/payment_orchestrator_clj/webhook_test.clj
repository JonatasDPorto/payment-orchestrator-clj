(ns payment-orchestrator-clj.webhook-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.api.webhook :as api]
            [payment-orchestrator-clj.payment.domain :as domain]
            [payment-orchestrator-clj.payment.repository :as payment-repository]
            [payment-orchestrator-clj.webhook.repository :as event-repository]
            [payment-orchestrator-clj.webhook.service :as service])
  (:import [java.io ByteArrayInputStream]
           [java.nio.charset StandardCharsets]
           [java.time Instant]
           [java.util UUID]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]))

(def fixed-clock #(Instant/ofEpochSecond 1700000000))
(def webhook-secret "whsec_test_secret")

(defrecord InMemoryPayments [payments]
  payment-repository/PaymentRepository
  (save-payment! [this payment] (payment-repository/save-payment! this payment {}))
  (save-payment! [_ payment _] (swap! payments assoc (:payment/id payment) payment) payment)
  (create-payment-idempotently! [_ payment _ _] {:outcome :created :payment payment})
  (record-provider-result! [_ payment _ _] (swap! payments assoc (:payment/id payment) payment) payment)
  (find-payment [_ payment-id] (get @payments payment-id)))

(defrecord InMemoryEvents [events references payments]
  event-repository/ProviderEventRepository
  (enqueue! [_ event _]
    (if (some #(= (:provider-event/dedupe-key event) (:provider-event/dedupe-key %)) (vals @events))
      {:outcome :duplicate}
      (do (swap! events assoc (:provider-event/id event) (assoc event :provider-event/status :provider-event.status/pending))
          {:outcome :accepted :event event})))
  (pending-events [_] (->> @events vals (filter #(= :provider-event.status/pending (:provider-event/status %))) vec))
  (payment-by-provider-reference [_ _ reference]
    (when-let [payment-id (get @references reference)]
      (get @payments payment-id)))
  (mark-processed! [_ event-id payment-id _]
    (swap! events update event-id assoc :provider-event/status :provider-event.status/processed :provider-event/payment payment-id))
  (mark-ignored! [_ event-id _]
    (swap! events update event-id assoc :provider-event/status :provider-event.status/ignored))
  (record-processing-error! [_ event-id error-code _]
    (swap! events update event-id assoc :provider-event/error error-code)))

(defn- hex [bytes]
  (apply str (map #(format "%02x" (bit-and 0xff %)) bytes)))

(defn- signature [raw-body]
  (let [mac (Mac/getInstance "HmacSHA256")
        timestamp 1700000000]
    (.init mac (SecretKeySpec. (.getBytes webhook-secret StandardCharsets/UTF_8) "HmacSHA256"))
    (str "t=" timestamp ",v1=" (hex (.doFinal mac (.getBytes (str timestamp "." raw-body) StandardCharsets/UTF_8))))))

(defn- request [raw-body signature-header]
  {:headers {"stripe-signature" signature-header}
   :body (ByteArrayInputStream. (.getBytes raw-body StandardCharsets/UTF_8))})

(defn- dependencies [payment-id]
  (let [payments (atom {payment-id (domain/transition
                                    (domain/new-payment {:id payment-id :customer-id "customer-123" :amount 100
                                                         :currency :BRL :method :payment.method/card
                                                         :occurred-at (fixed-clock)})
                                    :payment.status/processing (fixed-clock))})
        events (atom {})
        dispatched (atom 0)]
    {:payments (->InMemoryPayments payments)
     :provider-events (->InMemoryEvents events (atom {"pi_webhook" payment-id}) payments)
     :stripe-webhook-secret webhook-secret
     :clock fixed-clock
     :id-generator #(UUID/randomUUID)
     :dispatcher (fn [_] (swap! dispatched inc))
     :events events
     :payments-atom payments
     :dispatched dispatched}))

(def succeeded-event
  "{\"id\":\"evt_webhook_1\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_webhook\"}}}")

(deftest invalid-signature-is-rejected-before-parsing-or-persisting
  (let [dependencies (dependencies (UUID/randomUUID))
        response ((api/stripe-handler dependencies) (request "not-json" "t=1700000000,v1=bad"))]
    (is (= 400 (:status response)))
    (is (empty? @(:events dependencies)))))

(deftest duplicate-webhook-is-acknowledged-once-and-causes-one-payment-transition
  (let [payment-id (UUID/randomUUID)
        dependencies (dependencies payment-id)
        handler (api/stripe-handler dependencies)
        signed-request #(request succeeded-event (signature succeeded-event))]
    (is (= 200 (:status (handler (signed-request)))))
    (is (= 200 (:status (handler (signed-request)))))
    (is (= 1 @(:dispatched dependencies)))
    (is (= 1 (count @(:events dependencies))))
    (service/process-pending! dependencies)
    (service/process-pending! dependencies)
    (is (= :payment.status/paid (:payment/status (get @(:payments-atom dependencies) payment-id))))
    (is (= :provider-event.status/processed
           (:provider-event/status (first (vals @(:events dependencies))))))))

(deftest boleto-voucher-is-settled-only-by-the-asynchronous-provider-webhook
  (let [payment-id (UUID/randomUUID)
        boleto-payment (-> (domain/new-payment {:id payment-id :customer-id "customer-123" :amount 1000
                                                 :currency :BRL :method :payment.method/boleto :occurred-at (fixed-clock)})
                           (domain/transition :payment.status/processing (fixed-clock))
                           (domain/transition :payment.status/requires-action (fixed-clock)))
        payments (atom {payment-id boleto-payment})
        events (atom {})
        dependencies {:payments (->InMemoryPayments payments)
                      :provider-events (->InMemoryEvents events (atom {"pi_boleto_webhook" payment-id}) payments)
                      :clock fixed-clock :id-generator #(UUID/randomUUID)}
        raw-body "{\"id\":\"evt_boleto_succeeded\",\"type\":\"payment_intent.succeeded\",\"data\":{\"object\":{\"id\":\"pi_boleto_webhook\"}}}"]
    (is (= :accepted (:outcome (service/enqueue-stripe-event! dependencies raw-body))))
    (is (= :payment.status/requires-action (:payment/status (get @payments payment-id))))
    (service/process-pending! dependencies)
    (is (= :payment.status/paid (:payment/status (get @payments payment-id))))
    (is (= :provider-event.status/processed (:provider-event/status (first (vals @events)))))))

(deftest unknown-event-is-persisted-and-ignored-safely
  (let [dependencies (dependencies (UUID/randomUUID))
        raw-body "{\"id\":\"evt_unknown\",\"type\":\"customer.created\",\"data\":{\"object\":{\"id\":\"cus_x\"}}}"
        response ((api/stripe-handler dependencies) (request raw-body (signature raw-body)))]
    (is (= 200 (:status response)))
    (service/process-pending! dependencies)
    (is (= :provider-event.status/ignored
           (:provider-event/status (first (vals @(:events dependencies))))))))

(deftest missing-payment-remains-recoverable-with-an-observable-error
  (let [dependencies (dependencies (UUID/randomUUID))
        events (:events dependencies)
        event {:provider-event/id (UUID/randomUUID) :provider-event/dedupe-key "stripe:evt_missing"
               :provider-event/provider :stripe :provider-event/external-id "evt_missing"
               :provider-event/type "payment_intent.succeeded" :provider-event/provider-reference "pi_missing"
               :provider-event/payload-sha256 "hash" :provider-event/received-at (fixed-clock)}]
    (event-repository/enqueue! (:provider-events dependencies) event {:source :source/test})
    (service/process-pending! dependencies)
    (is (= :provider-event.status/pending (:provider-event/status (get @events (:provider-event/id event)))))
    (is (= "payment_not_found" (:provider-event/error (get @events (:provider-event/id event)))))))

(deftest processor-failure-remains-pending-and-is-reprocessed-after-restart
  (let [payment-id (UUID/randomUUID)
        dependencies (dependencies payment-id)
        raw-body succeeded-event
        events (:events dependencies)]
    (swap! (:payments-atom dependencies) assoc payment-id
           (domain/new-payment {:id payment-id :customer-id "customer-123" :amount 100
                                :currency :BRL :method :payment.method/card :occurred-at (fixed-clock)}))
    (service/enqueue-stripe-event! dependencies raw-body)
    (service/process-pending! dependencies)
    (is (= :provider-event.status/pending (:provider-event/status (first (vals @events)))))
    (is (= "processing_failed" (:provider-event/error (first (vals @events)))))
    (swap! (:payments-atom dependencies) update payment-id domain/transition :payment.status/processing (fixed-clock))
    (service/process-pending! dependencies)
    (is (= :payment.status/paid (:payment/status (get @(:payments-atom dependencies) payment-id))))))
