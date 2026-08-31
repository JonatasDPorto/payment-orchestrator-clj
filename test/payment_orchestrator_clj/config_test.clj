(ns payment-orchestrator-clj.config-test
  (:require [clojure.test :refer [deftest is]]
            [payment-orchestrator-clj.config :as config]))

(deftest base-config-is-loadable
  (is (= {:payment-orchestrator-clj/service-name "payment-orchestrator-clj"
          :payment-orchestrator-clj/environment :development}
         (select-keys (config/base-config)
                      [:payment-orchestrator-clj/service-name :payment-orchestrator-clj/environment]))))
