(ns novasia.payload-redactor.test-runner
  (:require [clojure.test :as test] [novasia.payload-redactor.core-test]))
(defn -main [& _]
  (let [result (test/run-tests 'novasia.payload-redactor.core-test)]
    (when (pos? (+ (:fail result) (:error result))) (System/exit 1))))
