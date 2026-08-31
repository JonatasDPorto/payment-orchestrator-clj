(ns payment-orchestrator-clj.event.kafka
  (:require [clojure.data.json :as json]
            [payment-orchestrator-clj.event.port :as port])
  (:import [org.apache.kafka.clients.producer KafkaProducer ProducerRecord]
           [org.apache.kafka.common.serialization StringSerializer]
           [java.util Properties]))

(defrecord KafkaEventProducer [producer topic]
  port/EventProducer
  (publish! [_ event]
    (.get (.send producer (ProducerRecord. topic (:event/aggregate-id event)
                                                 (json/write-str {:eventId (:event/id event)
                                                                  :type (:event/type event)
                                                                  :version (:event/version event)
                                                                  :aggregateId (:event/aggregate-id event)
                                                                  :occurredAt (:event/occurred-at event)
                                                                  :data (:event/data event)}))))
    event))

(defn new-producer [{:keys [bootstrap-servers topic]
                     :or {topic "payment-events"}}]
  (let [properties (doto (Properties.)
                     (.put "bootstrap.servers" bootstrap-servers)
                     (.put "key.serializer" (.getName StringSerializer))
                     (.put "value.serializer" (.getName StringSerializer))
                     (.put "acks" "all"))]
    (->KafkaEventProducer (KafkaProducer. properties) topic)))
