(ns rexster-explorer.server
  (:require [compojure.core :refer [defroutes GET]]
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
