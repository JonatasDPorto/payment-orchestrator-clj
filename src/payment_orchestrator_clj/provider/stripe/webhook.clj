(ns payment-orchestrator-clj.provider.stripe.webhook
  "Stripe-specific webhook verification and event mapping."
  (:require [clojure.data.json :as json]
            [clojure.string :as string])
  (:import [java.nio.charset StandardCharsets]
           [java.math BigInteger]
           [java.security MessageDigest]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]
           [java.time Instant]))

(defn- sha256 [value]
  (let [digest (MessageDigest/getInstance "SHA-256")]
    (format "%064x" (BigInteger. 1 (.digest digest (.getBytes value StandardCharsets/UTF_8))))))

(defn payload-hash [raw-body]
  (sha256 raw-body))

(defn- hmac-sha256 [secret payload]
  (let [mac (Mac/getInstance "HmacSHA256")]
    (.init mac (SecretKeySpec. (.getBytes secret StandardCharsets/UTF_8) "HmacSHA256"))
    (.doFinal mac (.getBytes payload StandardCharsets/UTF_8))))

(defn- hex->bytes [hex]
  (try
    (.parseHex (java.util.HexFormat/of) hex)
    (catch IllegalArgumentException _ nil)))

(defn- signature-values [signature-header]
  (reduce (fn [values piece]
            (let [[key value] (string/split piece #"=" 2)]
              (update values key (fnil conj []) value)))
          {} (string/split (or signature-header "") #",")))

(defn valid-signature?
  ([raw-body signature-header secret clock]
   (valid-signature? raw-body signature-header secret clock 300))
  ([raw-body signature-header secret clock tolerance-seconds]
   (let [values (signature-values signature-header)
         timestamp (some-> (first (get values "t")) parse-long)
         candidates (get values "v1")]
     (and timestamp
          (<= (Math/abs (- (.getEpochSecond ^Instant (clock)) timestamp)) tolerance-seconds)
          (some #(when-let [candidate (hex->bytes %)]
                   (MessageDigest/isEqual (hmac-sha256 secret (str timestamp "." raw-body)) candidate))
                candidates)))))

(defn parse-event [raw-body]
  (let [event (json/read-str raw-body :key-fn keyword)]
    {:provider-event/provider :stripe
     :provider-event/external-id (:id event)
     :provider-event/type (:type event)
     :provider-event/webhook-identity (:account event)
     :provider-event/provider-reference (get-in event [:data :object :id])}))

(defn canonical-payment-status [event-type]
  (case event-type
    "payment_intent.succeeded" :payment.status/paid
    "payment_intent.processing" :payment.status/processing
    "payment_intent.payment_failed" :payment.status/failed
    "payment_intent.canceled" :payment.status/cancelled
    "payment_intent.requires_action" :payment.status/requires-action
    nil))
