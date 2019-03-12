(ns de.lovelyco.watcher.sqs
  (require [clojure.tools.logging :as log]
           [cheshire.core :refer [parse-string]]
           [clojure.core.match :refer [match]]
           [de.lovelyco.watcher.util :refer [run-loop]]
           [de.lovelyco.watcher.tag-parser :refer [parse-docker-tag]])
  (import [com.amazonaws.services.sqs AmazonSQSClientBuilder]
          [com.amazonaws.services.sqs.model DeleteMessageRequest Message]
          [java.util.concurrent LinkedBlockingQueue]))

(defprotocol PackageEvent
  "A set of parameters uniquely describing something to deploy"
  (image [_])
  (tag [_])
  (branch [_])
  (githubRef [_])
  (spec [_])
  (revision [_])
  (deploymentName [_]))

(defonce sqs-client (delay (.build (AmazonSQSClientBuilder/standard))))
(defonce bq (LinkedBlockingQueue.)) ; PackageEvents are placed onto this queue

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
        reg-id (get params "registryId")
        repo-name (get params "repositoryName")
        tag (get params "imageTag")
        full-image (str reg-id ".dkr.ecr." (get raw "region") ".amazonaws.com/" repo-name ":" tag)
        metadata (parse-docker-tag tag)
        branch (:branch metadata)
        sanitized-branch (.toLowerCase branch)]
    (reify PackageEvent
      (image [_] repo-name)
      (tag [_] tag)
      (branch [_] sanitized-branch)
      (githubRef [_] branch)
      (spec [_] full-image)
      (revision [_] (:revision metadata))
      (deploymentName [_] (str (aget (.split repo-name "/") 1) "-" sanitized-branch))
      (toString [_] full-image))))

(defn- route-message [^Message msg]
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

(defn start []
  "Start listening for and enqueuing SQS / PackageEvent messages"
  (run-loop "sqs-message-listener" (doseq [msg (get-messages)]
              (route-message msg))))

(defn msg-queue [] bq)
