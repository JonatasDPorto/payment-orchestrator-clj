(ns payment-orchestrator-clj.event.relay
  (:require [datomic.client.api :as d]
            [payment-orchestrator-clj.event.port :as producer])
  (:import [java.nio.charset StandardCharsets]
           [java.time Instant]
           [java.util Date UUID]))

(def relay-name "payment-event-relay")

(defn- checkpoint [connection]
  (d/pull (d/db connection) '[:relay/last-t] [:relay/consumer-name relay-name]))

(defn- save-checkpoint! [connection t now]
  (let [existing (checkpoint connection)]
    (d/transact connection {:tx-data [(cond-> {:relay/last-t t :relay/updated-at (Date/from now)}
                                          existing (assoc :db/id [:relay/consumer-name relay-name])
                                          (nil? existing) (assoc :relay/id (UUID/randomUUID)
                                                                   :relay/consumer-name relay-name))]})))

(defn- payment-status-datom? [database datom]
  (= :payment/status (:db/ident (d/pull database [:db/ident] (.a datom)))))

(defn- status-event [connection {:keys [t data]} datom]
  (when (.added datom)
    (let [database (d/as-of (d/db connection) t)
          payment (d/pull database [:payment/id :payment/amount :payment/currency] (.e datom))
          status (.v datom)
          event-id (str (UUID/nameUUIDFromBytes (.getBytes (str t ":" (:payment/id payment) ":" status)
                                                           StandardCharsets/UTF_8)))]
      {:event/id event-id
       :event/type (str "payment." (name status))
       :event/version 1
       :event/aggregate-id (str (:payment/id payment))
       :event/occurred-at (str (Instant/now))
       :event/data {:status (name status) :amount (:payment/amount payment)
                    :currency (name (:payment/currency payment))}})))

(defn- transaction-events [connection transaction]
  (let [database (d/db connection)]
    (->> (:data transaction)
         (filter #(payment-status-datom? database %))
         (keep #(status-event connection transaction %)))))

(defn run-once!
  "Publishes from the Datomic log and advances the checkpoint only after each tx publishes."
  [{:keys [connection producer clock metrics]}]
  (let [last-t (:relay/last-t (checkpoint connection))
        transactions (d/tx-range connection {:start (when last-t (inc last-t))})]
    (reduce (fn [published transaction]
              (doseq [event (transaction-events connection transaction)]
                (producer/publish! producer event))
              (save-checkpoint! connection (:t transaction) (clock))
              (let [count (count (transaction-events connection transaction))]
                (when metrics (swap! metrics update "event_relay_published_total" (fnil + 0) count))
                (+ published count)))
            0 transactions)))
