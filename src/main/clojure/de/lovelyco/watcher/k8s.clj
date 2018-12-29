(ns de.lovelyco.watcher.k8s
  (require [de.lovelyco.watcher.config :as config]
           [cheshire.core :refer [parse-string]])
  (import
   [io.kubernetes.client.apis AppsV1Api CoreV1Api]
   [io.kubernetes.client Configuration]
   [io.kubernetes.client.util Config]
   [io.kubernetes.client.models V1Deployment]
   [java.util.concurrent TimeUnit]))

(defn- get-timeout []
  (Integer/parseInt
   (config/get-value "K8S_API_TIMEOUT" (do "5"))))

(def client (delay (let [c (Config/defaultClient)]
                     (doto (.getHttpClient c) (.setReadTimeout (get-timeout) TimeUnit/SECONDS))
                     (Configuration/setDefaultApiClient c)
                     (CoreV1Api.))))

(defn- decide-api [k8s-object]
  (if (= V1Deployment (class k8s-object))
    (AppsV1Api.)
    (CoreV1Api.)))

(defn- create-or-update [k8s-object]
  )
