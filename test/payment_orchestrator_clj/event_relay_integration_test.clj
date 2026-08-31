(ns payment-orchestrator-clj.event-relay-integration-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.event.port :as port]
            [payment-orchestrator-clj.event.relay :as relay]
            [payment-orchestrator-clj.payment.datomic-repository :as datomic]
            [payment-orchestrator-clj.payment.domain :as domain]
            [payment-orchestrator-clj.payment.repository :as payments])
  (:import [java.time Instant] [java.util UUID]))

(defrecord RecordingProducer [events fail?]
  port/EventProducer
  (publish! [_ event]
    (swap! events conj event)
    (when @fail? (throw (ex-info "Kafka unavailable" {:retryable? true})))
    event))

(defn- payment []
  (domain/new-payment {:id (UUID/randomUUID) :customer-id "customer-relay" :amount 1000
                       :currency :BRL :method :payment.method/card
                       :occurred-at (Instant/parse "2026-08-31T15:00:00Z")}))

(deftest relay-derives-ordered-events-and-checkpoints-after-publication
  (support/with-test-database
   (fn [connection]
     (let [repository (datomic/new-repository connection)
           created (payment)
           processing (domain/transition created :payment.status/processing (Instant/parse "2026-08-31T15:01:00Z"))
           paid (domain/transition processing :payment.status/paid (Instant/parse "2026-08-31T15:02:00Z"))
           events (atom [])
           dependencies {:connection connection :producer (->RecordingProducer events (atom false))
                         :clock #(Instant/parse "2026-08-31T15:03:00Z")}]
       (doseq [value [created processing paid]]
         (payments/save-payment! repository value {:source :source/test}))
       (is (= 3 (relay/run-once! dependencies)))
       (is (= ["payment.created" "payment.processing" "payment.paid"] (mapv :event/type @events)))
       (is (= 0 (relay/run-once! dependencies)))))))

(deftest failed-publication-does-not-advance-checkpoint-and-replays-on-restart
  (support/with-test-database
   (fn [connection]
     (let [repository (datomic/new-repository connection)
           created (payment)
           events (atom [])
           fail? (atom true)
           dependencies {:connection connection :producer (->RecordingProducer events fail?) :clock #(Instant/now)}]
       (payments/save-payment! repository created {:source :source/test})
       (try (relay/run-once! dependencies) (is false "Expected publication failure")
            (catch clojure.lang.ExceptionInfo _))
       (reset! fail? false)
       (relay/run-once! dependencies)
       (is (= 2 (count @events)))
       (is (= (map :event/id @events) (repeat 2 (:event/id (first @events)))))))))
