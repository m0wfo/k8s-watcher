(ns de.lovelyco.watcher.package-events
  (import [com.mowforth.dockerparser Parsers]
          [java.util.concurrent TimeUnit])
  (require [clojure.tools.logging :as log]
           [de.lovelyco.watcher.tag-parser :refer [parse-docker-tag]]))

(defprotocol PackageEvent
  "A set of parameters uniquely describing something to deploy"
  (image [_])
  (tag [_]) ; TODO rename
  (branch [_])
  (githubRef [_])
  (spec [_])
  (revision [_])
  (deploymentName [_]))

(defn sanitize [^String raw]
  (let [lower (.toLowerCase raw)
        clean (clojure.string/replace lower #"[\W_]" "-")]
    (if (> (.length clean) 60) ; truncate if longer
      (.substring clean 0 60)
      clean)))

(defn new-package-event
  ([^String full-image-name]
    (let [docker-image (-> (Parsers/getParser) (.parseImageName full-image-name))
          host (.get (.getHost docker-image))
          repo-name (clojure.string/join "/" (.getNameComponents docker-image))
          image-tag (.get (.getTag docker-image))]
      (new-package-event host repo-name image-tag)))
  ([^String repo-host ^String repo-name ^String image-tag]
   (log/debug "Creating PackageEvent repo-host =" repo-host "repo-name =" repo-name "image-tag =" image-tag)
   (let [metadata (parse-docker-tag image-tag)
         branch (:branch metadata)
         sanitized-branch (sanitize branch)
         sanitized-package (sanitize (aget (.split repo-name "/") 1))
         full-image (str repo-host "/" repo-name ":" image-tag)]
     (reify PackageEvent
       (image [_] repo-name)
       (tag [_] image-tag)
       (branch [_] sanitized-branch)
       (githubRef [_] branch)
       (spec [_] full-image)
       (revision [_] (:revision metadata))
       (deploymentName [_] (str sanitized-package  "-" sanitized-branch))
       (toString [_] full-image)))))

(defn submit-package-event [event ^java.util.concurrent.BlockingQueue bq]
  (let [success (.offer bq event 5 (TimeUnit/SECONDS))]
    (if-not success
      (throw (IllegalStateException. "Couldn't submit PackageEvent to queue")))))
