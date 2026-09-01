(ns payment-orchestrator-clj.consumer-webhook.service-test
  (:require [clojure.test :refer [deftest is]] [payment-orchestrator-clj.consumer-webhook.service :as service])
  (:import [java.time Instant] [java.util UUID]))
(defrecord MemoryRepo [items]
  service/DeliveryRepository
  (enqueue! [_ d] (if (some #(= (:delivery/dedupe-key %) (:delivery/dedupe-key d)) @items) {:outcome :duplicate} (do (swap! items conj d) {:outcome :accepted})) )
  (pending! [_] (filterv #(= :delivery.status/pending (:delivery/status %)) @items))
  (mark-delivered! [_ id r] (swap! items #(mapv (fn [x] (if (= id (:delivery/id x)) (assoc x :delivery/status :delivery.status/delivered :delivery/log r) x)) %)))
  (mark-retry! [_ id r] (swap! items #(mapv (fn [x] (if (= id (:delivery/id x)) (update (assoc x :delivery/log r) :delivery/attempts inc) x)) %)))
  (move-to-dead-letter! [_ id r] (swap! items #(mapv (fn [x] (if (= id (:delivery/id x)) (assoc x :delivery/status :delivery.status/dead-letter :delivery/log r) x)) %))))
(deftest signs-retries-dead-letters-and-deduplicates
  (let [repo (->MemoryRepo (atom [])) deps {:repository repo :id-generator #(UUID/randomUUID) :clock #(Instant/now) :secret "test-secret" :sender (fn [_ _ h] (is (string? (get h "X-Payment-Orchestrator-Signature"))) {:status 500}) :max-attempts 2} event {:event/id "evt-1" :event/type "payment.paid"}]
    (is (= :accepted (:outcome (service/enqueue-event! deps "https://consumer.test/hook" event))))
    (is (= :duplicate (:outcome (service/enqueue-event! deps "https://consumer.test/hook" event))))
    (service/deliver-pending! deps) (service/deliver-pending! deps)
    (is (= :delivery.status/dead-letter (:delivery/status (first @(:items repo)))))))
