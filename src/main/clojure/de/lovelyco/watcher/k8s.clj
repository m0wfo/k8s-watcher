(ns de.lovelyco.watcher.k8s
  (require [de.lovelyco.watcher.util :as util]
           [de.lovelyco.watcher.notifier :as notifier]
           [clojure.tools.logging :as log])
  (import
   [io.kubernetes.client.apis AppsV1Api BatchV1Api CoreV1Api]
   [io.kubernetes.client Configuration]
   [io.kubernetes.client.util Config]
   [io.kubernetes.client.models V1Deployment V1DeleteOptions V1Service V1Job]
   [java.util.concurrent TimeUnit]))

(defonce ^:private client (delay (let [timeout (Integer/parseInt (util/get-value "K8S_API_TIMEOUT" "5"))
                                       c (Config/defaultClient)]
                                   (.setDebugging c true)
                                   (doto (.getHttpClient c) (.setReadTimeout timeout TimeUnit/SECONDS))
                                   (Configuration/setDefaultApiClient c))))

(defonce ^:private k8s-ns (util/get-value "K8S_NAMESPACE" "default"))

(defmulti deploy class)

(defmethod deploy V1Deployment [spec]
  (log/debug "Deploying a V1Deployment object")
  (let [api (AppsV1Api.)
        name (-> spec .getMetadata .getName)
        selector (str "metadata.name=" name)
        list (.getItems (.listNamespacedDeployment api k8s-ns nil nil selector true nil nil nil nil false))]
    (if (empty? list)
      (.createNamespacedDeployment api k8s-ns spec nil)
      (.replaceNamespacedDeployment api name k8s-ns spec nil))))

(defmethod deploy V1Service [spec]
  (log/debug "Deploying V1Service object")
  (let [api (CoreV1Api.)
        name (-> spec .getMetadata .getName)
        selector (str "metadata.name=" name)
        list (.getItems (.listNamespacedService api k8s-ns nil nil selector true nil nil nil nil false))]
    (if (empty? list)
      (.createNamespacedService api k8s-ns spec nil)
      (let [current (first list)]
        ; update metadata
        (-> current .getMetadata (.name (-> spec .getMetadata .getName)))
        (-> current .getMetadata (.labels (-> spec .getMetadata .getLabels)))
        ; update spec
        (-> current .getSpec (.ports (-> spec .getSpec .getPorts)))
        (-> current .getSpec (.selector (-> spec .getSpec .getSelector)))
        (.replaceNamespacedService api name k8s-ns current nil)))))

(defmethod deploy V1Job [spec]
  (log/debug "Deploying V1Job object")
  (let [api (BatchV1Api.)
        name (-> spec .getMetadata .getName)
        selector (str "metadata.name=" name)
        list (.getItems (.listNamespacedJob api k8s-ns nil nil selector true nil nil nil nil false))]
    (.createNamespacedJob api k8s-ns spec nil)))

(defmethod deploy :default [a] (throw (IllegalArgumentException. (str "Don't know how to deploy " (type a)))))

; Public API

(defn deploy-specs [specs]
  @client
  (log/debug "Deploying specs" specs)
  (doall (map (fn [s]
                (deploy s)
                (notifier/notify-k8s-deploy s)) specs)))
