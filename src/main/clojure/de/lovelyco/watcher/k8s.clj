(ns de.lovelyco.watcher.k8s
  (require [de.lovelyco.watcher.util :as util]
           [clojure.tools.logging :as log])
  (import
   [io.kubernetes.client.apis AppsV1Api CoreV1Api]
   [io.kubernetes.client Configuration]
   [io.kubernetes.client.util Config]
   [io.kubernetes.client.models V1Deployment V1Service V1PersistentVolumeClaim]
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
      (.replaceNamespacedService api name k8s-ns spec nil))))

(defmethod deploy V1PersistentVolumeClaim [spec]
  (let [api (CoreV1Api.)
        name (-> spec .getMetadata .getName)
        selector (str "metadata.name=" name)
        list (.getItems (.listNamespacedPersistentVolumeClaim api k8s-ns nil nil selector true nil nil nil nil false))]
    (if (empty? list)
      (.createNamespacedPersistentVolumeClaim api k8s-ns spec nil)
      (.replaceNamespacedPersistentVolumeClaim api name k8s-ns spec nil))))

(defmethod deploy :default [a] (throw (IllegalArgumentException. (str "Don't know how to deploy " (type a)))))

; Public API

(defn deploy-specs [specs]
  @client
  (log/debug "Deploying specs" specs)
  (doall (map #(deploy %) specs)))
