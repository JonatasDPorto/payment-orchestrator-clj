(ns payment-orchestrator-clj.ledger.domain
  "Pure immutable double-entry ledger rules. Amounts are integer minor units."
  (:import [java.time Instant]
           [java.util UUID]))

(def processor-receivable "processor-receivable")
(def merchant-payable "merchant-payable")

(def default-accounts
  [{:ledger-account/code processor-receivable :ledger-account/type :ledger-account.type/asset}
   {:ledger-account/code merchant-payable :ledger-account/type :ledger-account.type/liability}])

(defn- invalid! [message data]
  (throw (ex-info message (assoc data :ledger/invalid? true))))

(defn posting [{:posting/keys [id account side amount currency] :as value}]
  (when-not (instance? UUID id) (invalid! "Posting requires UUID id" {:posting value}))
  (when-not (and (string? account) (seq account)) (invalid! "Posting requires account code" {:posting value}))
  (when-not (#{:posting.side/debit :posting.side/credit} side) (invalid! "Posting side is invalid" {:posting value}))
  (when-not (and (integer? amount) (pos? amount)) (invalid! "Posting amount must be positive" {:posting value}))
  (when-not (keyword? currency) (invalid! "Posting requires currency" {:posting value}))
  value)

(defn balanced? [postings]
  (= (reduce + 0 (map #(if (= :posting.side/debit (:posting/side %)) (:posting/amount %) 0) postings))
     (reduce + 0 (map #(if (= :posting.side/credit (:posting/side %)) (:posting/amount %) 0) postings))))

(defn journal [{:journal/keys [id payment type dedupe-key created-at postings] :as value}]
  (when-not (instance? UUID id) (invalid! "Journal requires UUID id" {:journal value}))
  (when-not (instance? UUID payment) (invalid! "Journal requires payment UUID" {:journal value}))
  (when-not (keyword? type) (invalid! "Journal requires type" {:journal value}))
  (when-not (and (string? dedupe-key) (seq dedupe-key)) (invalid! "Journal requires dedupe key" {:journal value}))
  (when-not (instance? Instant created-at) (invalid! "Journal requires Instant creation time" {:journal value}))
  (when-not (>= (count postings) 2) (invalid! "Journal requires at least two postings" {:journal value}))
  (doseq [entry postings] (posting entry))
  (when-not (= 1 (count (set (map :posting/currency postings))))
    (invalid! "Journal postings must use one currency" {:journal value}))
  (when-not (balanced? postings)
    (invalid! "Journal debits and credits must balance" {:journal value :error/code :ledger/unbalanced-journal}))
  value)

(defn payment-settled-journal [payment id-generator occurred-at]
  (journal {:journal/id (id-generator)
            :journal/payment (:payment/id payment)
            :journal/type :journal.type/payment-settled
            :journal/dedupe-key (str "payment-settled:" (:payment/id payment))
            :journal/created-at occurred-at
            :journal/postings [(posting {:posting/id (id-generator) :posting/account processor-receivable
                                         :posting/side :posting.side/debit :posting/amount (:payment/amount payment)
                                         :posting/currency (:payment/currency payment)})
                              (posting {:posting/id (id-generator) :posting/account merchant-payable
                                         :posting/side :posting.side/credit :posting/amount (:payment/amount payment)
                                         :posting/currency (:payment/currency payment)})]}))
