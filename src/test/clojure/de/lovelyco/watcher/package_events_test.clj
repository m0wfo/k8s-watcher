(ns de.lovelyco.watcher.package-events-test
  (require [de.lovelyco.watcher.package-events :as pe])
  (use [clojure test]))

(deftest test-no-double-slash
  (let [e (pe/new-package-event "1234.dkr.ecr.eu-west-1.amazonaws.com" "websummit/calendar" "rc-ABC-73-some-feature-v0.0.1-6581887")]
    (is (= "1234.dkr.ecr.eu-west-1.amazonaws.com/websummit/calendar:rc-ABC-73-some-feature-v0.0.1-6581887" (.spec e)))))

(deftest sanitize-uppercase
  (let [input "feature-INP-295"]
    (is (= "feature-inp-295" (pe/sanitize input)))))

(deftest sanitize-underscore
  (let [input "feature_INP-295"]
    (is (= "feature-inp-295" (pe/sanitize input)))))

(deftest sanitize-weird-characters
  (let [chars ["%" "$" "." "*" "/" "~" ":"]]
    (doseq [c chars]
      (let [raw (str "feature" c "do-something-INP-456")]
        (is (= "feature-do-something-inp-456" (pe/sanitize raw)))))))

(deftest test-long-names
  (let [long-name "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]
    (is (= 60 (.length (pe/sanitize long-name))))))

(deftest something
  (let [period "860825859229.dkr.ecr.eu-west-1.amazonaws.com/websummit/calendar:rc-use.Wildcard_certificate-v0.0.1-c903711"]
    (pe/new-package-event period)))