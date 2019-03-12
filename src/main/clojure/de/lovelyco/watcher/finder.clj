(ns de.lovelyco.watcher.finder
  (require [de.lovelyco.watcher.util :as util]
           [cheshire.core :refer [parse-string]]
           [clojure.tools.logging :as log])
  (import [com.fasterxml.jackson.databind ObjectMapper]
          [com.fasterxml.jackson.dataformat.yaml YAMLFactory]
          [io.kubernetes.client JSON]
          [io.kubernetes.client.models V1Deployment]
          [okhttp3 Credentials OkHttpClient Request]
          [java.util Base64]))

(defonce client (OkHttpClient.))
(defonce yaml-reader (ObjectMapper. (YAMLFactory.)))
(defonce json-writer (ObjectMapper.))

(defonce gh-root "https://api.github.com/repos/")

(defn call-github [target]
  (log/debug "Calling" target "...")
  (let [user (util/get-value "GITHUB_USER")
        token (util/get-value "GITHUB_TOKEN")
        auth (str "token " token)
        request (-> (okhttp3.Request$Builder.) (.header "Authorization" auth) (.url target) .build)]
    (let [resp (-> client (.newCall request) .execute)]
      (if (.isSuccessful resp)
        (-> resp  .body .string parse-string)
        (throw (IllegalStateException. (str "Request to " target " failed with status code " (.code resp))))))))

(defn- list-files [e]
  (log/debug "Listing all files for PackageEvent" e)
  (let [resp (call-github (str gh-root (.image e) "/contents/deployment?ref=" (.githubRef e)))]
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

(defn- template [yaml e]
  "Right now the only supported manifest variable is IMAGE_SPEC."
  (let [img (clojure.string/replace yaml #"\$\{IMAGE_SPEC\}" (.spec e))
        app-name (.deploymentName e)]
    (log/debug "Replacing instances of ${APP} with" app-name "in spec")
    (clojure.string/replace img #"\$\{APP\}" app-name)))

(defprotocol K8sConversion
  "Convert a String (known to be a well-formed JSON K8s object) into an equivalent K8s domain object"
  (to-k8s [_]))

(extend String
  K8sConversion
  {:to-k8s (fn [this]
             (let [parsed (parse-string this)
                   object-kind (or (get parsed "kind") (throw (IllegalArgumentException. "'kind' field not present in json")))
                   klass (Class/forName (str "io.kubernetes.client.models.V1" object-kind))]
               (.deserialize (JSON.) this klass)))})

(defn- set-labels [k8s-object e]
  (let [labels {"service" (aget (.split (.image e) "/") 1) "branch" (.branch e) "revision" (.revision e)}]
    (-> k8s-object .getMetadata (.labels labels))
    (if (instance? V1Deployment k8s-object)
      ; set labels at the pod-level if we're dealing with a Deployment object
      (let [pod-meta (-> k8s-object .getSpec .getTemplate .getMetadata)]
        (doseq [kvp labels]
          (.putLabelsItem pod-meta (first kvp) (second kvp)))))
    k8s-object))

(defn get-k8s-objects [e]
  "Use a PackageEvent to scan the appropriate GitHub repo for deployment manifests.
  Read these and parse them into K8s objects for the client library to use."
  (->> (list-files e)
       (map #(get-file-contents %))
       (map #(yaml->json %))
       (flatten)
       (map #(template % e))
       (map #(to-k8s %))
       (map #(set-labels % e))))
