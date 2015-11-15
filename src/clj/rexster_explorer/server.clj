(ns rexster-explorer.server
  (:gen-class)
  (:use [ring.middleware.gzip])
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [resources]]
            [ring.adapter.jetty :refer [run-jetty]]
            [cemerick.url :refer [url url-decode]]
            [clj-http.client :as http]))

(defroutes graph-proxy
  (GET "/graph-proxy/:eurl" [eurl :as request]
       (let [resp (-> eurl
                      url-decode
                      (http/get {:throw-exceptions false}))]
         {:status (:status resp)
          :headers {"Content-Type" (-> resp
                                       :headers
                                       (#(% "Content-Type")))}
          :body (:body resp)})))

(defroutes app-routes
  graph-proxy
  (resources "/"))

(def app
  (-> app-routes
      (wrap-gzip)))

(defn -main []
  (run-jetty app {:port 8080}))
