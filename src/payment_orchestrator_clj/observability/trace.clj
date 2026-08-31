(ns payment-orchestrator-clj.observability.trace)

(def ^:dynamic *spans* nil)
(defn with-span [name f]
  (let [spans (or *spans* (atom [])) started (System/nanoTime)]
    (binding [*spans* spans]
      (let [result (f)]
        (swap! spans conj {:name name :duration-ms (/ (- (System/nanoTime) started) 1000000.0)})
        result))))
