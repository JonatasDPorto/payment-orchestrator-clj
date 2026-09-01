(ns payment-orchestrator-clj.observability.metrics
  (:require [clojure.string :as string]))

(defn registry [] (atom {}))
(defn inc! [registry metric] (swap! registry update metric (fnil inc 0)))
(defn observe! [registry metric value] (swap! registry assoc metric (double value)))
(defn render [registry]
  (->> @registry (sort-by key)
       (map (fn [[metric value]] (str metric " " value)))
       (string/join "\n")))
