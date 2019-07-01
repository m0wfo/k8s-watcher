(ns de.lovelyco.watcher.circle-listener
  (import [io.jsonwebtoken Jwts JwtException SignatureAlgorithm]
          [io.jsonwebtoken.security Keys SignatureException]
          [java.io ByteArrayInputStream InputStreamReader]
          [java.util Date]
          [java.util.concurrent.atomic AtomicReference]
          [java.time.temporal ChronoUnit]
          [org.bouncycastle.openssl PEMParser]
          [org.bouncycastle.openssl.jcajce JcaPEMKeyConverter])
  (require [cheshire.core :refer [generate-string parse-stream]]
           [clojure.tools.logging :as log]
           [clojure.core.match :refer [match]]
           [de.lovelyco.watcher.circle-client :refer [build-is-legit?]]
           [de.lovelyco.watcher.package-events :refer [new-package-event, submit-package-event]]
           [de.lovelyco.watcher.util :refer [get-value]]
           [compojure.core :refer [defroutes POST]]
           [compojure.route :as route]
           [ring.adapter.jetty :refer [run-jetty]]
           [ring.middleware.reload :refer [wrap-reload]])
  (:gen-class))

(defn- get-signing-key []
  (let [buffer (InputStreamReader. (ByteArrayInputStream. (.getBytes (get-value "JWT_KEY"))))
        pem-parser (PEMParser. buffer)
        converter (JcaPEMKeyConverter. )]
    (.getPrivate (.getKeyPair converter (.readObject pem-parser)))))

(defonce signing-key (delay (get-signing-key)))

(defonce bad-request {:status 400})
(defonce unauthorized {:status 401})

(defonce queue-ref (AtomicReference.))

(defn- plus-time-delay [^Date date]
  (Date/from (-> date .toInstant (.plus 30 (ChronoUnit/MINUTES)))))

(defn- attempt-generate-jwt [params]
  (let [project (get params "x-circle-project")
        revision (get params "x-circle-revision")
        build-number (get params "x-circle-build-number")]
    (if (build-is-legit? project revision build-number)
      (let [now (Date.)
            subject (str "[" project "," revision "," build-number "]")
            jwt (-> (Jwts/builder)
                    (.setNotBefore now)
                    (.setIssuedAt now)
                    (.setExpiration (plus-time-delay now))
                    (.setAudience "circle")
                    (.setSubject subject) (.signWith @signing-key) .compact)]
        {:status 200 :headers {"Content-Type" "application/jwt"} :body jwt})
      unauthorized)))

(defn- validate-jwt [auth-header]
  (if (re-matches #"Bearer (.+)" auth-header)
    (let [token (clojure.string/replace auth-header #"Bearer " "")]
      (log/debug "Client presented JWS:" token)
      (try
        (let [jwt (-> (Jwts/parser) (.setSigningKey @signing-key) (.parseClaimsJws token))]
          (log/debug "Parsed JWT:" jwt)
          jwt)
        (catch JwtException e
          (log/warn "Client presented invalid token" token))))))

(defn- initiate-deplyment [request]
  (let [rq (parse-stream (clojure.java.io/reader request))
        image (get rq "image")
        event (new-package-event image)]
    (try
      (log/info "Initiating deployment with image" image)
      (submit-package-event event (.get queue-ref))
      {:status 202 :body (generate-string {:deployment-name (.deploymentName event)})}
      (catch Exception e
        (log/warn "Deployment initiation failed" e)
        bad-request))))

(defroutes routes
  (POST "/get-token" request
    (match [(:headers request)]
      [{"x-circle-project" _ "x-circle-build-number" _ "x-circle-revision" _}] (attempt-generate-jwt (:headers request))
      :else bad-request))
  (POST "/deploy" request
    (if (validate-jwt (get-in request [:headers "authorization"]))
      (initiate-deplyment (:body request))
      unauthorized))
  (route/not-found {:status 404}))

(def reloadable-app
  (wrap-reload #'routes))

(defn start [bq]
  (log/info "Starting CircleCI listener")
  (.set queue-ref bq)
  (run-jetty reloadable-app {:port 8080 :daemon? true :join? false}))
