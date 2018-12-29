(ns de.lovelyco.watcher.config
  (require [clojure.core.match :refer [match]]))

(defn get-value
  ([key] (get-value key #(throw (IllegalArgumentException. (str "Value for key " % " is not present")))))
  ([key absent-fn]
   (let [val (System/getenv key)]
     (match [val]
       [""] (absent-fn key)
       [nil] (absent-fn key)
       [_] val))))
