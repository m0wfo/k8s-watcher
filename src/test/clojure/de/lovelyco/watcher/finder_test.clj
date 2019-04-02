(ns de.lovelyco.watcher.finder-test
  (require [clojure.tools.logging :as log]
           [de.lovelyco.watcher.finder :as finder]
           [de.lovelyco.watcher.package-events :as pe]
           [de.lovelyco.watcher.util :refer [get-resource]])
  (use [clojure test]))

(defn- test-event [repo tag branch]
  (reify pe/PackageEvent
    (image [_] repo)
    (tag [_] tag)
    (branch [_] branch)
    (githubRef [_] branch)
    (spec [_] "SPEC_FOR_TEST_EVENT")
    (revision [_] "foo")))

(deftest test-handle-malformed-events
  (is (thrown? Exception (finder/get-k8s-objects (test-event nil nil nil)))))

(deftest test-get-env-label-no-env-specified
  (System/setProperty "WORKING_ENV" "something")
  (let [res-str (get-resource "testconfig.yml")
        k8s (->> res-str finder/yaml->json (map #(finder/to-k8s-object %)) (filter #(finder/select-env % (test-event "foo" "bar" "baz"))))]
    (is (= 1 (count k8s)))))

(deftest test-get-env-label-with-environment-specified
  (System/setProperty "WORKING_ENV" "notprod")
  (let [res-str (get-resource "prodconfig.yml")
        k8s (->> res-str finder/yaml->json (map #(finder/to-k8s-object %)) (filter #(finder/select-env % (test-event "foo" "bar" "baz"))))]
  (is (= 0 (count k8s)))))

(deftest fail-quietly-custom-resource
  (let [res-str (get-resource "customresource.yml")
        k8s (->> res-str finder/yaml->json (map #(finder/to-k8s-object %)) (filter #(not (nil? %))))]
    (is (= 0 (count k8s)))))