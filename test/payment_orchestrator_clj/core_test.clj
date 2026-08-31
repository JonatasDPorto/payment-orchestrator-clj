(ns payment-orchestrator-clj.core-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.core :as core]))

(deftest application-info-exposes-bootstrap-metadata
  (is (= {:payment-orchestrator-clj/service-name "payment-orchestrator-clj"
          :payment-orchestrator-clj/environment :development}
         (core/application-info))))
