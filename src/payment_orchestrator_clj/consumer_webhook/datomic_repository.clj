(ns payment-orchestrator-clj.consumer-webhook.datomic-repository
  (:require [datomic.client.api :as d] [clojure.data.json :as json] [payment-orchestrator-clj.consumer-webhook.service :as service])
  (:import [java.util Date]))
(def pattern '[:consumer-delivery/id :consumer-delivery/dedupe-key :consumer-delivery/endpoint :consumer-delivery/event-id :consumer-delivery/event-type :consumer-delivery/payload :consumer-delivery/attempts :consumer-delivery/status :consumer-delivery/last-status :consumer-delivery/created-at])
(defn- decode [x] (when x {:delivery/id (:consumer-delivery/id x) :delivery/dedupe-key (:consumer-delivery/dedupe-key x) :delivery/endpoint (:consumer-delivery/endpoint x) :delivery/event (json/read-str (:consumer-delivery/payload x) :key-fn keyword) :delivery/attempts (:consumer-delivery/attempts x) :delivery/status (:consumer-delivery/status x)}))
(defrecord Repo [connection]
  service/DeliveryRepository
  (enqueue! [_ x] (if (d/pull (d/db connection) '[:consumer-delivery/id] [:consumer-delivery/dedupe-key (:delivery/dedupe-key x)])
                   {:outcome :duplicate}
                   (try (d/transact connection {:tx-data [{:consumer-delivery/id (:delivery/id x) :consumer-delivery/dedupe-key (:delivery/dedupe-key x) :consumer-delivery/endpoint (:delivery/endpoint x) :consumer-delivery/event-id (:event/id (:delivery/event x)) :consumer-delivery/event-type (:event/type (:delivery/event x)) :consumer-delivery/payload (json/write-str (:delivery/event x)) :consumer-delivery/attempts 0 :consumer-delivery/status :delivery.status/pending :consumer-delivery/created-at (Date/from (:delivery/created-at x))}]}) {:outcome :accepted} (catch clojure.lang.ExceptionInfo _ {:outcome :duplicate}))))
  (pending! [_] (mapv #(decode (first %)) (d/q '[:find (pull ?d pattern) :in $ pattern :where [?d :consumer-delivery/status :delivery.status/pending]] (d/db connection) pattern)))
  (mark-delivered! [_ id r] (d/transact connection {:tx-data [{:db/id [:consumer-delivery/id id] :consumer-delivery/status :delivery.status/delivered :consumer-delivery/last-status (:status r)}]}))
  (mark-retry! [_ id r] (let [x (d/pull (d/db connection) '[:consumer-delivery/attempts] [:consumer-delivery/id id])] (d/transact connection {:tx-data [{:db/id [:consumer-delivery/id id] :consumer-delivery/attempts (inc (:consumer-delivery/attempts x)) :consumer-delivery/last-status (:status r)}]})))
  (move-to-dead-letter! [_ id r] (d/transact connection {:tx-data [{:db/id [:consumer-delivery/id id] :consumer-delivery/status :delivery.status/dead-letter :consumer-delivery/last-status (:status r)}]})))
(defn new-repository [connection] (->Repo connection))
