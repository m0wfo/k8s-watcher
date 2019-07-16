(ns de.lovelyco.watcher.k8s-test
  (require 
   [de.lovelyco.watcher.finder :as finder]
   [de.lovelyco.watcher.k8s :as k8s]
   [de.lovelyco.watcher.util :refer [get-resource]])
  (use [clojure test]))

(deftest test-inject-env-var
  (let [res-str (get-resource "prodconfig.yml")
        spec (first (->> res-str finder/yaml->json (map #(finder/to-k8s-object %))))
        injected (k8s/inject-service-name spec)]
    (println injected)
    (is (not (nil? injected)))))
