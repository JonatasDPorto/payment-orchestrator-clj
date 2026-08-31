(ns payment-orchestrator-clj.logging-test
  (:require [clojure.test :refer [deftest is]])
  (:import [org.slf4j LoggerFactory]))

(deftest slf4j-has-a-runtime-provider
  (is (= "org.slf4j.simple.SimpleLoggerFactory"
         (.getName (class (LoggerFactory/getILoggerFactory))))))
