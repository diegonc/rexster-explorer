(ns rexster-explorer.server
  (:gen-class)
  (:use [ring.middleware.gzip]
        [ring.middleware.params]
        [ring.middleware.reload])
  (:require [compojure.core :refer [defroutes GET]]
            [compojure.route :refer [resources]]
            [ring.util.response :as resp]
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
  (GET "/" [] (resp/content-type (resp/resource-response "public/index.html") "text/html"))
  (resources "/"))

(def app
  (-> app-routes
      (wrap-params)
      (wrap-gzip)))

(def figwheel-handler
  (-> graph-proxy
      (wrap-params)
      (wrap-reload)
      (wrap-gzip)))

(defn -main []
  (run-jetty app {:port 8080}))
