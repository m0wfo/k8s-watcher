(ns de.lovelyco.watcher.notifier
  (require [de.lovelyco.watcher.slack :as slack]
           [de.lovelyco.watcher.util :refer [get-value]]))

; Public API

(defn notify-starting [e]
  (let [msg (str "Deploying new change. image=" (.image e)
                 " branch=" (.branch e) " env=" (get-value "WORKING_ENV")
                 " revision=" (.revision e))]
    (slack/notify msg)
    e))
