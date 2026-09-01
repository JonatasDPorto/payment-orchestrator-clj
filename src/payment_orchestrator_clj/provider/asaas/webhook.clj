(ns payment-orchestrator-clj.provider.asaas.webhook
  "Asaas webhook token verification and canonical event mapping."
  (:require [clojure.data.json :as json])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]
           [java.math BigInteger]))

(defn valid-token? [provided expected]
  (and (string? provided) (string? expected)
       (MessageDigest/isEqual (.getBytes provided StandardCharsets/UTF_8)
                              (.getBytes expected StandardCharsets/UTF_8))))

(defn payload-hash [raw]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (format "%064x" (BigInteger. 1 (.digest digest (.getBytes raw StandardCharsets/UTF_8))))))

(defn parse-event [raw]
  (let [body (json/read-str raw :key-fn keyword)]
    {:provider-event/provider :asaas
     :provider-event/external-id (:id body)
     :provider-event/type (:event body)
     :provider-event/provider-reference (get-in body [:payment :id])}))

(defn canonical-payment-status [event]
  (case event
    ("PAYMENT_RECEIVED" "PAYMENT_CONFIRMED") :payment.status/paid
    "PAYMENT_OVERDUE" :payment.status/processing
    ("PAYMENT_DELETED" "PAYMENT_CANCELLED") :payment.status/cancelled
    "PAYMENT_REFUNDED" :payment.status/paid
    nil))
