(ns de.lovelyco.watcher.util
  (require [clojure.core.match :refer [match]]
           [clojure.tools.logging :as log])
  (import [java.util.concurrent Executors ThreadFactory]))

(defn get-resource [^String filename]
  "Read a file at a given resource path"
  (slurp (clojure.java.io/resource filename)))

(defn get-value
  "Get a config value from environment var _key_, with an optional function invoked in the nil case.
  Throws an IllegalArgumentException by default."
  ([key] (get-value key #(throw (IllegalArgumentException. (str "Value for key " % " is not present")))))
  ([key absent]
   (let [val (System/getenv key)
         absent-fn (if (instance? clojure.lang.IFn absent)
                     absent
                     (fn [x] absent))]
     (match [val]
       [""] (absent-fn key)
       [nil] (absent-fn key)
       [_] val))))

(defmacro run-loop [name & body]
  `(let [factory# (reify ThreadFactory
                    (newThread [_ runnable#]
                               (log/info "Starting" ~name)
                               (Thread. runnable# ~name)))
         executor# (Executors/newSingleThreadExecutor factory#)
         work# (reify Runnable
                 (run [_] (while (not (-> (Thread/currentThread) .isInterrupted))
                            (try
                              ~@body
                              (catch Exception e# (log/error "Exception in loop" e#))))
                   (log/info "Run loop shut down")))]
     (.submit executor# work#)
     executor#))
