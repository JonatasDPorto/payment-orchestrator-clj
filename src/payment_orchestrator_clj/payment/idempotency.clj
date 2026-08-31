(ns payment-orchestrator-clj.payment.idempotency
  "Pure normalization and hashing for consumer idempotency."
  (:require [clojure.string :as string])
  (:import [java.nio.charset StandardCharsets]
           [java.security MessageDigest]))

(def command-fields [:customer-id :amount :currency :method])

(defn normalized-command [command]
  (into (sorted-map) (select-keys command command-fields)))

(defn request-hash [command]
  (let [bytes (.digest (MessageDigest/getInstance "SHA-256")
                       (.getBytes (pr-str (normalized-command command)) StandardCharsets/UTF_8))]
    (string/join (map #(format "%02x" (bit-and % 0xff)) bytes))))
