(ns de.lovelyco.watcher.sqs
  (require [clojure.tools.logging :as log]
           [cheshire.core :refer [parse-string]]
           [clojure.core.match :refer [match]]
           [de.lovelyco.watcher.util :refer [run-loop]])
  (import [com.amazonaws.services.sqs AmazonSQSClientBuilder]
          [com.amazonaws.services.sqs.model DeleteMessageRequest Message]
          [java.util.concurrent LinkedBlockingQueue]
          [java.util.regex Pattern]))

(defprotocol PackageEvent
  "A set of parameters uniquely describing something to deploy"
  (image [_])
  (tag [_])
  (branch [_])
  (spec [_])
  (revision [_]))

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

(def ^:private tag-pattern (delay (Pattern/compile "(rc-)?(?<branch>[a-zA-Z0-9-]+)(-v\\d\\.\\d\\.\\d(-(?<commit>[a-z0-9]+)))?")))

(defn- parse-tag [^String tag]
  "Infer git info from the docker image tag. Fails fast if tag does not match regex."
  (let [matcher (-> @tag-pattern (.matcher tag))]
    (if (.matches matcher)
      {:branch (.group matcher "branch") :revision (.group matcher "commit")}
      (throw (IllegalArgumentException. (str "Tag " tag " does not match spec, cannot parse"))))))

(defn- to-event [^java.util.Map raw]
  "Strip info of interest from deserialized JSON and return a PackageEvent object"
  (let [params (get-in raw ["detail" "requestParameters"])
        reg-id (get params "registryId")
        repo-name (get params "repositoryName")
        tag (get params "imageTag")
        full-image (str reg-id ".dkr.ecr." (get raw "region") ".amazonaws.com/" repo-name ":" tag)
        metadata (parse-tag tag)]
    (reify PackageEvent
      (image [_] repo-name)
      (tag [_] tag)
      (branch [_] (:branch metadata))
      (spec [_] full-image)
      (revision [_] (:revision metadata))
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
