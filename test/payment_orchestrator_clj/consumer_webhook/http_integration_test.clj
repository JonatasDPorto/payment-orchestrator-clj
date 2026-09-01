(ns payment-orchestrator-clj.consumer-webhook.http-integration-test
  (:require [clojure.test :refer [deftest is]] [payment-orchestrator-clj.consumer-webhook.service :as service]
            [payment-orchestrator-clj.consumer-webhook.http :as http])
  (:import [com.sun.net.httpserver HttpServer HttpHandler] [java.net InetSocketAddress] [java.time Instant] [java.util UUID]))
(defrecord Repo [state]
  service/DeliveryRepository
  (enqueue! [_ d] (if (some #(= (:delivery/dedupe-key %) (:delivery/dedupe-key d)) @state) {:outcome :duplicate} (do (swap! state conj d) {:outcome :accepted})))
  (pending! [_] (filterv #(= :delivery.status/pending (:delivery/status %)) @state))
  (mark-delivered! [_ id _] (swap! state #(mapv (fn [d] (if (= id (:delivery/id d)) (assoc d :delivery/status :delivery.status/delivered) d)) %)))
  (mark-retry! [_ id _] (swap! state #(mapv (fn [d] (if (= id (:delivery/id d)) (update d :delivery/attempts inc) d)) %)))
  (move-to-dead-letter! [_ id _] (swap! state #(mapv (fn [d] (if (= id (:delivery/id d)) (assoc d :delivery/status :delivery.status/dead-letter) d)) %))))
(deftest real-local-http-delivery-is-signed-and-delivered
  (let [received (atom nil) server (HttpServer/create (InetSocketAddress. 0) 0) repo (->Repo (atom []))]
    (.createContext server "/hook" (reify HttpHandler (handle [_ x] (reset! received {:headers (into {} (.getRequestHeaders x)) :body (slurp (.getRequestBody x))}) (.sendResponseHeaders x 204 -1) (.close (.getResponseBody x)))))
    (.start server)
    (try
      (let [endpoint (str "http://localhost:" (.getPort (.getAddress server)) "/hook") event {:event/id "evt-http" :event/type "payment.paid"}
            deps {:repository repo :id-generator #(UUID/randomUUID) :clock #(Instant/now) :secret "shared" :sender (http/sender {})}]
        (is (= :accepted (:outcome (service/enqueue-event! deps endpoint event))))
        (service/deliver-pending! deps)
        (is (= :delivery.status/delivered (:delivery/status (first @(:state repo)))))
        (is (= (service/signature "shared" (:body @received)) (first (get-in @received [:headers "X-payment-orchestrator-signature"])))) )
      (finally (.stop server 0)))))
