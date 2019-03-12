(ns de.lovelyco.watcher.notifier
  (require [clojure.tools.logging :as log]
           [de.lovelyco.watcher.slack :as slack]
           [de.lovelyco.watcher.util :refer [get-value]]))

(defn- safe-notify [msg]
  (try
    (slack/notify msg)
    (catch Exception e (log/error e))))

; Public API

(defn notify-starting [e]
  (let [msg (str "Saw new image, attempting deployment. image=" (.image e)
                 " branch=" (.branch e) " env=" (get-value "WORKING_ENV")
                 " revision=" (.revision e))]
    (safe-notify msg)
    e))

(defn notify-available [e]
  (let [product (aget (.split (.image e) "/") 1)
        msg (str "Deploying, service should shortly be available at: " (get-value "BASE_URL") "/" (.deploymentName e) "/")]
    (safe-notify msg)
    e))

(defn notify-k8s-deploy [spec]
  (let [msg (str "Deployed K8s resource: type=" (.getKind spec) " name=" (-> spec .getMetadata .getName))]
    (safe-notify msg)))
