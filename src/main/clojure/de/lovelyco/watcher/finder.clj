(ns de.lovelyco.watcher.finder
  (require [de.lovelyco.watcher.config :as config]
           [cheshire.core :refer [parse-string]]
           [clojure.tools.logging :as log])
  (import [com.fasterxml.jackson.databind ObjectMapper]
          [com.fasterxml.jackson.dataformat.yaml YAMLFactory]
          [io.kubernetes.client JSON]
          [okhttp3 Credentials OkHttpClient Request]
          [java.util Base64]))

(defonce client (OkHttpClient.))
(defonce yaml-reader (ObjectMapper. (YAMLFactory.)))
(defonce json-writer (ObjectMapper.))

(defonce gh-root "https://api.github.com/repos/")

(defn call-github [target]
  (log/debug "Calling" target "...")
  (let [user (config/get-value "GITHUB_USER")
        token (config/get-value "GITHUB_TOKEN")
        auth (str "token " token)
        request (-> (okhttp3.Request$Builder.) (.header "Authorization" auth) (.url target) .build)]
    (let [resp (-> client (.newCall request) .execute)]
      (if (.isSuccessful resp)
        (-> resp  .body .string parse-string)
        (throw (IllegalStateException. (str "Request to " target " failed with status code " (.code resp))))))))

(defn- list-files [e]
  (log/debug "Listing all files for PackageEvent" e)
  (let [resp (call-github (str gh-root (.image e) "/contents/deployment?ref=" (.branch e)))]
    (map #(get % "url") resp)))

(defn- get-file-contents [path]
  (let [resp (call-github path)
        content (.replace (get resp "content") "\n" "")] ; github returns Base64 data with newlines
    (String. (.decode (Base64/getDecoder) content))))

(defn- convert [raw-yaml]
  (log/trace "Converting YAML " raw-yaml "into equivalent JSON object")
  (let [json-obj (.readValue yaml-reader raw-yaml Object)]
    (.writeValueAsString json-writer json-obj)))

(defn- yaml->json [raw-yaml]
  (map #(convert %) (clojure.string/split raw-yaml #"---")))

(defn- fill-vars [yaml e]
  "Right now the only supported manifest variable is IMAGE_SPEC."
  (clojure.string/replace yaml #"\$\{IMAGE_SPEC\}" (.spec e)))

(defprotocol K8sConversion
  "Convert a String (known to be a well-formed JSON K8s object) into an equivalent POJO"
  (to-k8s [_]))

(extend String
  K8sConversion
  {:to-k8s (fn [this]
             (let [parsed (parse-string this)
                   object-kind (or (get parsed "kind") (throw (IllegalArgumentException. "'kind' field not present in json")))
                   klass (Class/forName (str "io.kubernetes.client.models.V1" object-kind))]
               (.deserialize (JSON.) this klass)))})

(defn get-k8s-objects [e]
  "Use a PackageEvent to scan the appropriate GitHub repo for deployment manifests.
  Read these and parse them into K8s objects for the client library to use."
  (->> (list-files e)
       (map #(get-file-contents %))
       (map #(yaml->json %))
       (flatten)
       (map #(fill-vars % e))
       (map #(to-k8s %))))

(def test-event
  (reify de.lovelyco.watcher.sqs.PackageEvent
    (image [_] "websummit/storyzero")
    (tag [_] "wibble")
    (branch [_] "deployment-config")
    (spec [_] "SPEC_FOR_TEST_EVENT")))
