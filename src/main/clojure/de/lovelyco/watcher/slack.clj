(ns de.lovelyco.watcher.slack
  (require [de.lovelyco.watcher.util :as util]
           [clojure.tools.logging :as log])
  (import [okhttp3 OkHttpClient Request RequestBody MediaType]))

(defonce ^:private client (OkHttpClient.))
(defonce ^:private media-type (MediaType/get "application/json"))

(defn notify [^String e]
  (if-let [target (util/get-value "SLACK_WEBHOOK" nil)]
    (let [body (RequestBody/create media-type (str "{\"text\":\"" e "\"}"))
          request (-> (okhttp3.Request$Builder.) (.url target) (.post body) .build)
          response (-> client (.newCall request) .execute)]
      (log/trace "Sending Slack notification" e)
      (if (.isSuccessful response)
        (log/trace "Successfully posted notification about package" e)
        (throw (IllegalStateException. (str "Request to " target " failed with status code " (.code response))))))
    (log/info "Slack webhook not configured, skipping notification")))
