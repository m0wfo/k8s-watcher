(ns de.lovelyco.watcher.tag-parser
  (import [java.util.regex Pattern])
  (:require [de.lovelyco.watcher.util :refer [get-resource]]))

(defn- remove-version-and-commit [tokens]
  (let [minus-commit (drop-last 1 tokens)]
    (if (= "SNAPSHOT" (last minus-commit))
      (drop-last 2 minus-commit)
      (drop-last 1 minus-commit))))
; Public API

(defn parse-docker-tag [^String raw-tag]
  (let [tokens (clojure.string/split raw-tag #"-")
        pattern (Pattern/compile "(rc-)?(?<branch>.+)")
        revision (last tokens)
        branch (remove-version-and-commit tokens)
        matcher (.matcher pattern (clojure.string/join "-" branch))]
    (if (.matches matcher)
      {:branch (.group matcher "branch") :revision revision}
      (throw (IllegalArgumentException. (str "Couldn't parse tag " raw-tag))))))
