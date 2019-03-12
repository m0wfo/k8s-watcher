(ns de.lovelyco.watcher.finder-test
  (require [de.lovelyco.watcher.finder :as finder])
  (use [clojure test]))

(defn- test-event [repo tag branch]
  (reify de.lovelyco.watcher.sqs.PackageEvent
    (image [_] repo)
    (tag [_] tag)
    (branch [_] branch)
    (githubRef [_] branch)
    (spec [_] "SPEC_FOR_TEST_EVENT")
    (revision [_] "foo")))

(deftest test-handle-malformed-events
  (is (thrown? Exception (finder/get-k8s-objects (test-event nil nil nil)))))
