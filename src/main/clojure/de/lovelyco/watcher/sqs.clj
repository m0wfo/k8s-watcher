(ns de.lovelyco.watcher.sqs
  (require [clojure.tools.logging :as log]
           [cheshire.core :refer [parse-string]]
           [clojure.core.match :refer [match]]
           [de.lovelyco.watcher.package-events :refer [new-package-event]]
           [de.lovelyco.watcher.util :refer [run-loop]])
  (import [com.amazonaws.services.sqs AmazonSQSClientBuilder]
          [com.amazonaws.services.sqs.model DeleteMessageRequest Message]))

(defonce sqs-client (delay (.build (AmazonSQSClientBuilder/standard))))

(defn- find-sqs-queue [client]
  "Find the queue to poll based on the user-supplied name."
  (let [queue-list (.getQueueUrls (.listQueues client))]
    (first (filter #(.endsWith % "k8s-ecr-events-dev") queue-list))))

(def ^:private sqs-queue (delay (find-sqs-queue @sqs-client)))

(defn- get-messages
  "Poll a given SQS queue for any unclaimed messages."
  ([] (get-messages @sqs-client @sqs-queue))
  ([client queue]
   (log/info "Polling SQS queue" queue "for events...")
   (.getMessages (.receiveMessage client queue))))

(defn- delete-message
  ([msg] (delete-message @sqs-client @sqs-queue msg))
  ([client queue msg]
   (let [req (DeleteMessageRequest. queue (.getReceiptHandle msg))]
     (log/trace "Deleting message" msg)
     (.deleteMessage client req))))

(defn- to-event [^java.util.Map raw]
  "Strip info of interest from deserialized JSON and return a PackageEvent object"
  (let [params (get-in raw ["detail" "requestParameters"])
        repo-host (str (get params "registryId") ".dkr.ecr." (get raw "region") ".amazonaws.com")
        repo-name (get params "repositoryName")
        tag (get params "imageTag")]
    (new-package-event repo-host repo-name tag)))

(defn- route-message [^Message msg bq]
  (let [decoded (parse-string (.getBody msg))
        msg-type (get-in decoded ["detail" "eventName"])]
    (match [msg-type]
      ["PutImage"] (do
                     (log/info "Saw new docker image message" msg)
                     (try
                       (.put bq (to-event decoded))
                       (catch IllegalArgumentException e
                         (log/warn e))))
      [_] (log/trace "Not interested in message, discarding" msg))
    (delete-message msg)))

; Public API

(defn start [bq]
  "Start listening for and enqueuing SQS / PackageEvent messages"
  (run-loop "sqs-message-listener" (doseq [msg (get-messages)]
              (route-message msg bq))))
