(ns de.lovelyco.watcher.tag-parser-test
  (use [clojure test])
  (require [de.lovelyco.watcher.tag-parser :refer [parse-docker-tag]]))

(defn- test-parse [input branch revision]
  (let [parsed (parse-docker-tag input)]
    (and
     (is (= branch (get parsed :branch)))
     (is (= revision (get parsed :revision))))))

(deftest short-thing
  (test-parse "rc-pin-id-v0.0.1-edd92ee" "pin-id" "edd92ee"))

(deftest sd
  (test-parse "rc-some-long-string-of-text-v0.0.1-a9863bc" "some-long-string-of-text" "a9863bc"))

(deftest something
  (test-parse "rc-develop-v0.0.1-26864ec" "develop" "26864ec"))

(deftest something-else
  (test-parse "rc-refactor-auth-and-validations-v0.0.1-4d6e913" "refactor-auth-and-validations" "4d6e913"))

(deftest uppercase
  (test-parse "rc-INP-293-foo-v0.0.1-1a7f148" "INP-293-foo" "1a7f148"))

(deftest period
  (test-parse "rc-use.Some_words-v0.0.1-26864ec" "use.Some_words" "26864ec"))

(deftest missing-rc
  (test-parse "master-v0.0.1-c092963" "master" "c092963"))

(deftest snapshot
  (test-parse "master-v1.0.0-SNAPSHOT-7adf33c" "master" "7adf33c"))
