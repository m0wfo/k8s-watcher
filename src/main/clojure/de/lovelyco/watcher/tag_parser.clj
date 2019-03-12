(ns de.lovelyco.watcher.tag-parser
  (:require [de.lovelyco.watcher.util :refer [get-resource]]
            [instaparse.core :as insta]))

(def ^:private tag-parser
  (delay (insta/parser (get-resource "image-grammar.bnf") :output-format :enlive)))

(defn- stringify [token]
  (apply str (get (first token) :content)))

; Public API

(defn parse-docker-tag [^String raw-tag]
  (let [parse-tree (insta/parse @tag-parser raw-tag)
        tag (get parse-tree :content)
        branch (filter #(= :BRANCH (get % :tag)) tag)
        revision (filter #(= :REVISION (get % :tag)) tag)]
    (if (insta/failure? parse-tree)
      (throw (IllegalArgumentException. (str "Cannot parse input '" raw-tag "'"))))
    {:branch (stringify branch) :revision (stringify revision)}))
