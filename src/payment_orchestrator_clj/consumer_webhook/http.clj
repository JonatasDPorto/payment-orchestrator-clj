(ns payment-orchestrator-clj.consumer-webhook.http
  (:import [java.net URI] [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.time Duration]))
(defn sender [{:keys [connect-timeout-ms request-timeout-ms] :or {connect-timeout-ms 2000 request-timeout-ms 5000}}]
  (let [client (-> (HttpClient/newBuilder) (.connectTimeout (Duration/ofMillis connect-timeout-ms)) .build)]
    (fn [endpoint payload headers]
      (try
        (let [builder (HttpRequest/newBuilder (URI/create endpoint))
              request (reduce (fn [b [k v]] (.header b k v))
                              (-> builder (.timeout (Duration/ofMillis request-timeout-ms)) (.header "Content-Type" "application/json") (.POST (HttpRequest$BodyPublishers/ofString payload))) headers)
              response (.send client (.build request) (HttpResponse$BodyHandlers/ofString))]
          {:status (.statusCode response)})
        (catch Exception error {:status 599 :error (.getMessage error)})))))
