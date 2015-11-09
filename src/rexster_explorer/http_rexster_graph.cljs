(ns rexster-explorer.http-rexster-graph
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [rexster-explorer.rexster-graph :refer [RexsterGraph]]
            [cljs.core.async :as async :refer [chan put! <!]]
            [cemerick.url :refer [url url-encode]]
            [cljs-http.client :as http]))

(def ^:private graph-proxy "/graph-proxy/")

(defn- get-uri [url-parts]
  (let [res-url (apply url url-parts)
        enc-url (url-encode (str res-url))
        uri (str graph-proxy enc-url)]
    (go
      (<! (http/get uri)))))

(defn make-graph [server graph]
  (let [base-uri
        (url (str "http://"
                  server
                  "/graphs/"
                  (url-encode graph)))]
    (reify
      RexsterGraph
      (get-vertex [_ id]
        (get-uri [base-uri "vertices" (url-encode id)]))
      (get-edge [_ id]
        (get-uri [base-uri "edges" (url-encode id)]))
      (get-both-edges [_ id]
        (get-uri [base-uri "vertices"
                  (url-encode id) "bothE"]))
      (get-neighbourhood [this id]
        (throw (js/Error. "Not Implemented Yet"))))))
