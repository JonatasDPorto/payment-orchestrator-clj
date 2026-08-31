(ns payment-orchestrator-clj.performance
  "Small, dependency-free primitives shared by reproducible M15 load profiles."
  (:import [java.lang.management ManagementFactory]))

(defn generated-dataset [size]
  (mapv (fn [index]
          {:customer-id (str "perf-customer-" index)
           :amount (+ 1000 (mod index 9000))
           :currency "BRL"
           :method "card"
           :idempotency-key (str "perf-payment-" index)})
        (range size)))

(defn percentile [samples p]
  (let [ordered (vec (sort samples))
        position (int (Math/ceil (* p (count ordered))))]
    (nth ordered (max 0 (dec position)))))

(defn summary [samples elapsed-nanos]
  (let [durations (mapv :duration-nanos samples)
        operation-count (count samples)
        elapsed-ms (/ elapsed-nanos 1000000.0)]
    {:operations operation-count
     :throughput-per-second (if (pos? elapsed-ms) (* 1000.0 (/ operation-count elapsed-ms)) 0.0)
     :p50-ms (/ (percentile durations 0.50) 1000000.0)
     :p95-ms (/ (percentile durations 0.95) 1000000.0)
     :p99-ms (/ (percentile durations 0.99) 1000000.0)
     :error-rate (if (pos? operation-count) (/ (count (filter :error? samples)) operation-count) 0.0)}))

(defn measure! [operation]
  (let [started (System/nanoTime)]
    (try
      (operation)
      {:duration-nanos (- (System/nanoTime) started) :error? false}
      (catch Exception _
        {:duration-nanos (- (System/nanoTime) started) :error? true}))))

(defn runtime-snapshot []
  (let [runtime (Runtime/getRuntime)
        gc-beans (ManagementFactory/getGarbageCollectorMXBeans)]
    {:available-processors (.availableProcessors runtime)
     :heap-used-bytes (- (.totalMemory runtime) (.freeMemory runtime))
     :heap-max-bytes (.maxMemory runtime)
     :gc-collections (reduce + 0 (map #(max 0 (.getCollectionCount %)) gc-beans))
     :gc-time-ms (reduce + 0 (map #(max 0 (.getCollectionTime %)) gc-beans))}))
