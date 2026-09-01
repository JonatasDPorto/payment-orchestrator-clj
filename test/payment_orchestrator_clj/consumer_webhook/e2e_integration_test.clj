(ns payment-orchestrator-clj.consumer-webhook.e2e-integration-test
  (:require [clojure.test :refer [deftest is]] [payment-orchestrator-clj.datomic.test-support :as support]
            [payment-orchestrator-clj.consumer-webhook.service :as service] [payment-orchestrator-clj.consumer-webhook.http :as http]
            [payment-orchestrator-clj.consumer-webhook.datomic-repository :as datomic])
  (:import [com.sun.net.httpserver HttpServer HttpHandler] [java.net InetSocketAddress] [java.time Instant] [java.util UUID]))
(deftest payment-event-to-real-consumer-webhook-e2e
  (support/with-test-database (fn [connection]
    (let [seen (atom []) sender-result (atom nil) server (HttpServer/create (InetSocketAddress. 0) 0) repo (datomic/new-repository connection)]
      (.createContext server "/hook" (reify HttpHandler (handle [_ x] (swap! seen conj {:body (slurp (.getRequestBody x)) :signature (first (get (.getRequestHeaders x) "X-Payment-Orchestrator-Signature"))}) (.sendResponseHeaders x 204 -1) (.close (.getResponseBody x)))))
      (.start server)
      (try (let [endpoint (str "http://127.0.0.1:" (.getPort (.getAddress server)) "/hook") event {:event/id "payment-e2e-1" :event/type "payment.paid"}
                 real-sender (http/sender {}) deps {:repository repo :endpoints [endpoint] :id-generator #(UUID/randomUUID) :clock #(Instant/now) :secret "e2e-secret" :sender (fn [u p h] (let [r (real-sender u p h)] (reset! sender-result r) r)) :max-attempts 2}]
             (is (= [{:outcome :accepted}] (service/publish-payment-event! deps event)))
             (is (= [{:outcome :duplicate}] (service/publish-payment-event! deps event)))
             (is (= 1 (count (service/pending! repo))))
             (service/deliver-pending! deps)
             (is (empty? (service/pending! repo)) (str "endpoint=" endpoint " sender=" @sender-result " pending=" (service/pending! repo)))
             (is (= 1 (count @seen)))
             (when (seq @seen) (is (= (service/signature "e2e-secret" (:body (first @seen))) (:signature (first @seen))))))
           (finally (.stop server 0)))))))
