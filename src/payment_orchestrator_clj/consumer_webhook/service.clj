(ns payment-orchestrator-clj.consumer-webhook.service
  "At-least-once outbound webhook delivery with signed envelopes and durable-style state boundary."
  (:require [clojure.data.json :as json])
  (:import [javax.crypto Mac] [javax.crypto.spec SecretKeySpec] [java.nio.charset StandardCharsets]))

(defprotocol DeliveryRepository
  (enqueue! [repository delivery]) (pending! [repository]) (mark-delivered! [repository id result])
  (mark-retry! [repository id result]) (move-to-dead-letter! [repository id result]))

(defn signature [secret payload]
  (let [mac (Mac/getInstance "HmacSHA256")]
    (.init mac (SecretKeySpec. (.getBytes secret StandardCharsets/UTF_8) "HmacSHA256"))
    (format "%064x" (java.math.BigInteger. 1 (.doFinal mac (.getBytes payload StandardCharsets/UTF_8))))))

(defn enqueue-event! [{:keys [repository id-generator clock]} endpoint event]
  (let [dedupe (str endpoint ":" (:event/id event))]
    (enqueue! repository {:delivery/id (id-generator) :delivery/dedupe-key dedupe :delivery/endpoint endpoint
                          :delivery/event event :delivery/attempts 0 :delivery/status :delivery.status/pending
                          :delivery/created-at (clock)})))

(defn deliver-pending! [{:keys [repository secret sender max-attempts] :or {max-attempts 5}}]
  (mapv (fn [delivery]
          (let [payload (json/write-str (:delivery/event delivery))
                event-id (or (:event/id (:delivery/event delivery)) (:id (:delivery/event delivery)))
                result (try (sender (:delivery/endpoint delivery) payload {"X-Payment-Orchestrator-Signature" (signature secret payload)
                                                                            "X-Payment-Orchestrator-Event-Id" event-id})
                            (catch Exception _ {:status 599}))]
            (cond (<= 200 (:status result) 299) (mark-delivered! repository (:delivery/id delivery) result)
                  (>= (inc (:delivery/attempts delivery)) max-attempts) (move-to-dead-letter! repository (:delivery/id delivery) result)
                  :else (mark-retry! repository (:delivery/id delivery) result))))
        (pending! repository)))

(defn publish-payment-event! [{:keys [repository endpoints id-generator clock]} event]
  (mapv #(enqueue-event! {:repository repository :id-generator id-generator :clock clock} % event) endpoints))
