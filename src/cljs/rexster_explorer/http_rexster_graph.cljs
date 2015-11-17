(ns rexster-explorer.http-rexster-graph
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [rexster-explorer.rexster-graph :as rg]
            [cljs.core.async :as async :refer [<!]]
            [cemerick.url :refer [url url-encode]]
            [cljs-http.client :as http]))

(def ^:private graph-proxy "/graph-proxy/")

(defn- get-uri [url-parts]
  (let [res-url (apply url url-parts)
        enc-url (url-encode (str res-url))
        uri (str graph-proxy enc-url)]
    (go
      (let [r (<! (http/get uri))
            success (:success r)
            body (:body r)]
        {:success success
         :results (if success
                    (:results body)
                    body)}))))

(defn- collect-vertex-ids [edges]
  (->> edges
       (map #(vector (:_outV %) (:_inV %)))
       (reduce #(conj %1 (%2 0) (%2 1)) #{})
       seq))

(defn- produce-neighbourhood
  [graph {:keys [success results] :as edges}]
  (go
    (if-not success
      edges
      (let [vertex-ids (collect-vertex-ids results)
            vertex-chans (map #(rg/get-vertex graph %) vertex-ids)
            vertex-values (<! (async/map vector vertex-chans))]
        (if (every? :success vertex-values)
          (let [vertices-map (apply merge (map hash-map vertex-ids (map :results vertex-values)))]
            {:success true
             :results {:edges results
                       :vertices vertices-map}})
          (let [messages (reduce #(if-not (:success %2)
                                    (conj %1 (:results %2))
                                    %1)
                                 [] vertex-values)]
            {:success false
             :results messages}))))))

(defn make-graph [server graph]
  (let [base-uri
        (url (str "http://"
                  server
                  "/graphs/"
                  (url-encode graph)))]
    (reify
      rg/RexsterGraph
      (get-vertex [_ id]
        (get-uri [base-uri "vertices" (url-encode id)]))
      (get-edge [_ id]
        (get-uri [base-uri "edges" (url-encode id)]))
      (get-both-edges [_ id]
        (get-uri [base-uri "vertices"
                  (url-encode id) "bothE"]))
      (get-neighbourhood [this id]
        (go
          (let [edges (<! (rg/get-both-edges this id))
                res (<! (produce-neighbourhood this edges))]
            res))))))
