(ns de.lovelyco.watcher.core
  (require [clojure.tools.logging :as log]
           [clojure.tools.nrepl.server :as nrepl])
  (:gen-class))

(defprotocol EventStage
  (take-work [this]))

(defn -main [& args]
  (log/info "Hi, everybody!")
  (log/info "Starting nREPL server...")
  (nrepl/start-server :port 7888 :bind "0.0.0.0"))
