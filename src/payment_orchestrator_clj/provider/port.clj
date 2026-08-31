(ns payment-orchestrator-clj.provider.port)

(def provider-statuses
  #{:provider.status/processing :provider.status/requires-action :provider.status/succeeded
    :provider.status/failed :provider.status/cancelled})

(defprotocol PaymentGateway
  (capabilities [gateway])
  (create-payment! [gateway command])
  (fetch-payment [gateway reference])
  (cancel-payment! [gateway command])
  (refund-payment! [gateway command]))

(defn provider-error [category data]
  (ex-info (name category) (merge {:provider/error? true :provider/error category} data)))
