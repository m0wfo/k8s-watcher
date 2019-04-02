(ns de.lovelyco.watcher.circle-client
  (require [clojure.core.match :refer [match]]
           [clojure.tools.logging :as log]
           [de.lovelyco.watcher.util :refer [get-value]])
  (import [okhttp3 OkHttpClient Request]
          [com.fasterxml.jackson.databind ObjectMapper]))

(defonce client (OkHttpClient.))
(defonce mapper (ObjectMapper.))

(defn- get-build [project build-number]
  (let [url (str "https://circleci.com/api/v1.1/project/github/websummit/" project "/" build-number "?circle-token=" (get-value "CIRCLE_API_TOKEN"))
        request (-> (okhttp3.Request$Builder.) (.header "Accept" "application/json") (.url url) .build)
        resp (-> client (.newCall request) .execute)
        resp-string (-> resp .body .string)
        response-map (.readValue mapper resp-string java.util.Map)]
    (into {} response-map)))

(defn build-is-legit? [project revision build-number]
  (let [circle-response (get-build project build-number)
        build-number-int (Integer/parseInt build-number)]
    (match [circle-response]
      [{"vcs_revision" revision "build_num" build-number-int "lifecycle" "running"}] true
      :else false)))
