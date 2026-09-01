(ns payment-orchestrator-clj.dispute.domain)

(def statuses #{:dispute.status/needs-response :dispute.status/under-review :dispute.status/won :dispute.status/lost})
(def transitions {:dispute.status/needs-response #{:dispute.status/under-review :dispute.status/won :dispute.status/lost}
                  :dispute.status/under-review #{:dispute.status/won :dispute.status/lost}
                  :dispute.status/won #{} :dispute.status/lost #{}})

(defn new-dispute [{:keys [id payment-id amount currency reason provider provider-reference occurred-at]}]
  (when-not (and (uuid? id) (uuid? payment-id) (integer? amount) (pos? amount) (= currency :BRL)
                 (keyword? provider) (string? provider-reference))
    (throw (ex-info "invalid-dispute" {:error/code :dispute/invalid})))
  {:dispute/id id :dispute/payment-id payment-id :dispute/amount amount :dispute/currency currency
   :dispute/reason reason :dispute/provider provider :dispute/provider-reference provider-reference
   :dispute/status :dispute.status/needs-response :dispute/created-at occurred-at})

(defn transition [dispute status]
  (when-not (contains? (get transitions (:dispute/status dispute) #{}) status)
    (throw (ex-info "invalid-dispute-transition" {:error/code :dispute/invalid-transition
                                                    :from (:dispute/status dispute) :to status})))
  (assoc dispute :dispute/status status))
