(ns de.lovelyco.watcher.core
  (require [de.lovelyco.watcher.sqs :as sqs]
           [de.lovelyco.watcher.k8s :as k8s]
           [de.lovelyco.watcher.finder :as finder]
           [de.lovelyco.watcher.util :as util]
           [de.lovelyco.watcher.notifier :as notifier]
           [clojure.tools.logging :as log]
           [clojure.tools.nrepl.server :as nrepl])
  (import [java.util.concurrent TimeUnit])
  (:gen-class))

(defn -main [& args]
  (log/info (util/get-resource "logo.txt"))
  (sqs/start)
  (util/run-loop "event-processor"
   (if-let [event (.poll (sqs/msg-queue) 5 TimeUnit/SECONDS)]
     (-> event
         (notifier/notify-starting)
         (notifier/notify-available)
         (finder/get-k8s-objects)
         (k8s/deploy-specs)
         (log/info "Deployment of" event "finished"))))
  (log/info "Starting nREPL server...")
  (nrepl/start-server :port 7888 :bind "0.0.0.0"))
