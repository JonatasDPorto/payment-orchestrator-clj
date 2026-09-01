(ns payment-orchestrator-clj.provider.asaas.webhook
  "Asaas webhook token verification and canonical event mapping."
  (:require [clojure.data.json :as json]
            [payment-orchestrator-clj.security :as security])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]
           [java.math BigInteger]))

(defn valid-token? [provided expected]
  (security/secure= provided expected))

(defn payload-hash [raw]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (format "%064x" (BigInteger. 1 (.digest digest (.getBytes raw StandardCharsets/UTF_8))))))

(defn parse-event [raw]
  (try
    (let [body (json/read-str raw :key-fn keyword)]
      {:provider-event/provider :asaas
       :provider-event/external-id (:id body)
       :provider-event/type (:event body)
       :provider-event/provider-reference (get-in body [:payment :id])})
    ;; `data.json` reports malformed input through IOException subclasses. The
    ;; raw body is intentionally not included in the error data or logs.
    (catch java.io.IOException error
      (throw (ex-info "Asaas webhook body is invalid" {:error/code :webhook/invalid-event} error)))))

(defn canonical-payment-status [event]
  (case event
    ("PAYMENT_RECEIVED" "PAYMENT_CONFIRMED") :payment.status/paid
    "PAYMENT_OVERDUE" :payment.status/processing
    ("PAYMENT_DELETED" "PAYMENT_CANCELLED") :payment.status/cancelled
    "PAYMENT_REFUNDED" :payment.status/paid
    nil))
