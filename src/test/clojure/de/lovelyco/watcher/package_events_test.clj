(ns de.lovelyco.watcher.package-events-test
  (require [de.lovelyco.watcher.package-events :as pe])
  (use [clojure test]))

(deftest test-no-double-slash
  (let [e (pe/new-package-event "1234.dkr.ecr.eu-west-1.amazonaws.com" "websummit/calendar" "rc-ABC-73-some-feature-v0.0.1-6581887")]
    (is (= "1234.dkr.ecr.eu-west-1.amazonaws.com/websummit/calendar:rc-ABC-73-some-feature-v0.0.1-6581887" (.spec e)))))

; (deftest test-)