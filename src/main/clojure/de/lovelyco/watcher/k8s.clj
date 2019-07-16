(ns de.lovelyco.watcher.k8s
  (require [de.lovelyco.watcher.util :as util]
           [de.lovelyco.watcher.notifier :as notifier]
           [clojure.tools.logging :as log])
  (import
   [io.kubernetes.client.apis AppsV1Api BatchV1Api CoreV1Api ExtensionsV1beta1Api]
   [io.kubernetes.client Configuration]
   [io.kubernetes.client.util Config]
   [io.kubernetes.client.models V1Deployment V1DeleteOptions V1EnvVar V1Namespace V1Service V1Job]
   [java.util.concurrent TimeUnit]))

(defonce ^:private client (delay (let [timeout (Integer/parseInt (util/get-value "K8S_API_TIMEOUT" "5"))
                                       c (Config/defaultClient)]
                                   (.setDebugging c true)
                                   (doto (.getHttpClient c) (.setReadTimeout timeout TimeUnit/SECONDS))
                                   (Configuration/setDefaultApiClient c))))

(defonce ^:private k8s-ns (util/get-value "K8S_NAMESPACE" "default"))

(defn- get-name [spec] (-> spec .getMetadata .getName))

(defn inject-service-name [spec]
  "Inject APP and SERVICE environment variables for config management tools to infer correct config sets"
  (let [container-spec (-> spec .getSpec .getTemplate .getSpec)
        containers (concat (.getContainers container-spec) (.getInitContainers container-spec))
        deploy-name (doto (V1EnvVar.) (.name "APP") (.value (get-name spec)))
        service-name (doto (V1EnvVar.) (.name "SERVICE") (.value (-> spec .getMetadata .getLabels (.get "service"))))]
    (doall (doseq [container containers]
             (.addEnvItem container deploy-name)
             (.addEnvItem container service-name))))
  spec)

(defn- update-ingress [^V1Service spec]
  ; (let [api (ExtensionsV1beta1Api.)
  ;       service-shortname (-> spec .getMetadata .getLabels (.get "service"))
  ;       (ingress-name (util/get-value "INGRESS_NAME"))
  ;       ingress-def (.readNamespacedIngress api ingress-name k8s-ns nil nil nil)]
  ;   (log/debug ingress-def)))
  (log/info "Ingress update here"))

(defmulti deploy class)

(defmethod deploy V1Deployment [spec]
  (log/debug "Deploying a V1Deployment object")
  (let [api (AppsV1Api.)
        name (get-name spec)
        selector (str "metadata.name=" name)
        list (.getItems (.listNamespacedDeployment api k8s-ns nil nil selector true nil nil nil nil false))
        decorated (inject-service-name spec)]
    (if (empty? list)
      (.createNamespacedDeployment api k8s-ns decorated nil)
      (.replaceNamespacedDeployment api name k8s-ns decorated nil))))

(defmethod deploy V1Namespace [spec]
  (log/debug "Deploying V1Namespace object")
  (let [api (CoreV1Api.)]
    (.createNamespace api spec nil)))

(defmethod deploy V1Service [spec]
  (log/debug "Deploying V1Service object")
  (let [api (CoreV1Api.)
        name (get-name spec)
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
        (.replaceNamespacedService api name k8s-ns current nil)))
    (update-ingress spec)))

(defmethod deploy V1Job [spec]
  (log/debug "Deploying V1Job object")
  (let [api (BatchV1Api.)
        name (get-name spec)
        selector (str "metadata.name=" name)
        list (.getItems (.listNamespacedJob api k8s-ns nil nil selector true nil nil nil nil false))
        decorated (inject-service-name spec)]
    (if-not (empty? list)
      ; if there was an old job hanging around, clean it out first
      (let [delete-options (doto (V1DeleteOptions.) (.setPropagationPolicy "Background"))]
        (log/debug "Deleting old job before creating new one")
        (.deleteNamespacedJob api name k8s-ns delete-options nil nil nil nil)))
    (.createNamespacedJob api k8s-ns decorated nil)))

(defmethod deploy :default [a] (throw (IllegalArgumentException. (str "Don't know how to deploy " (type a)))))

; Public API

(defn deploy-specs [specs]
  @client
  (log/debug "Deploying specs" specs)
  (doall (map (fn [s]
                (deploy s)
                (notifier/notify-k8s-deploy s)) specs)))
