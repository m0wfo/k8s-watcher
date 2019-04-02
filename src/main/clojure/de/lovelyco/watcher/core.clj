(ns de.lovelyco.watcher.core
  (require [de.lovelyco.watcher.sqs :as sqs]
           [de.lovelyco.watcher.circle-listener :as circle-listener]
           [de.lovelyco.watcher.k8s :as k8s]
           [de.lovelyco.watcher.finder :as finder]
           [de.lovelyco.watcher.util :as util]
           [de.lovelyco.watcher.notifier :as notifier]
           [clojure.tools.logging :as log]
           [clojure.tools.nrepl.server :as nrepl])
  (import [java.util.concurrent LinkedBlockingQueue TimeUnit])
  (:gen-class))

(defonce bq (LinkedBlockingQueue.)) ; PackageEvents are placed onto this queue

(defn -main [& args]
  (log/info (util/get-resource "logo.txt"))

  (if (util/get-value "SQS_QUEUE" nil)
    (sqs/start bq))
  (if (util/get-value "CIRCLE_API_TOKEN" nil)
    (circle-listener/start bq))

  (util/run-loop "event-processor"
                 (if-let [event (.poll bq 5 TimeUnit/SECONDS)]
                   (-> event
                       (notifier/notify-starting)
                       (notifier/notify-available)
                       (finder/get-k8s-objects)
                       (k8s/deploy-specs)
                       (log/info "Deployment of" event "finished"))))
  (log/info "Starting nREPL server")
  (nrepl/start-server :port 7888 :bind "0.0.0.0"))
