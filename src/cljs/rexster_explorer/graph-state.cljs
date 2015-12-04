(ns ^:figwheel-always rexster-explorer.graph-state)

;; Module for centralizing access to the structure
;; describing the state of a graph.
;;
;; This state consists of the following pieces of
;; information:
;;
;;   - `:graph`     Represents the connection to
;;                  the rexster server from where
;;                  vertices and edges are retrieved.
;;
;;   - `:vertices`  The collection of vertices that
;;                  has been loaded and will be
;;                  displayed on the visualization.
;;
;;   - `:edges`     The collection of edges that
;;                  has been loaded and will be
;;                  displayed on the visualization.
;;
;;   - `:queries`   The collection of queries that has
;;                  been executed against the graph.
;;
;;                  Each query in this collection has
;;                  the following structure:
;;                     - `:script`   Where the script to
;;                                   execute is collected.
;;
;;                     - `:success`  The status of the last
;;                                   execution.
;;                                   `:empty` mean the query
;;                                   hasn't been executed yet.
;;                                   `true` or `false` give the
;;                                   execution status.
;;
;;                     - `:results`  Where the results of
;;                                   the script execution
;;                                   are stored.
;;
;;                     - `:messages` Where messages and errors
;;                                   related to the query execution
;;                                   are stored.
;;
;;                  Currently, the only query supported
;;                  is the one that consult the graph
;;                  context through a gremlin script (g.*)
;;                  and which is stored under the `:graph`
;;                  key.
;;
;;   - `:settings`  Where settings related to the graph and
;;                  its visualization are stored.
;;
;;                  Fields:
;;
;;                    - `:host-port` Host name and port used to
;;                      connect to the graph.
;;
;;                    - `:name` Name of the graph.
;;
;;                    - [`:vis-map` `:nodes`] Stores the mapping
;;                      from some field of a vertex received
;;                      through the connection to the fields
;;                      used by the visualization. It's a map of:
;;
;;                        `:id`    ==> keyword to extract the id
;;                        `:label` ==> keyword to extract the label
;;
;;                    - [`:vis-map` `:edges`] Stores the mapping
;;                      from some field of an edge received
;;                      through the connection to the fields
;;                      used by the visualization. It's a map of:
;;
;;                        `:id`    ==> keyword to extract the id
;;                        `:label` ==> keyword to extract the label
;;                        `:from`  ==> keyword to extract the id of
;;                                     the 'out' vertex
;;                        `:to`    ==> keyword to extract the id of
;;                                     the 'in' vertex
;;                      

(defn make-new-state []
  {:vertices {}
   :edges    {}
   :queries  {:graph {}}
   :settings {:vis-map {:nodes {}
                        :edges {}}}})

(defn set-graph [g gs] (assoc gs :graph g))

(defn set-settings [s gs] (assoc gs :settings s))

(defn- log [x] (.log js/console x) x)

(defn keywordize-settings [s]
  (let [s-nodes (-> s :vis-map :nodes)
        s-edges (-> s :vis-map :edges)
        k-nodes (->> s-nodes
                     (map #(update % 1 keyword))
                     flatten
                     (apply hash-map))
        k-edges (->> s-edges
                     (map #(update % 1 keyword))
                     flatten
                     (apply hash-map))]
    (-> s
        (assoc-in [:vis-map :nodes] k-nodes)
        (assoc-in [:vis-map :edges] k-edges))))

(defn get-loaded-vertices [gs]
  (->> gs
       :vertices
       (map second)))

(defn get-loaded-edges [gs]
  (->> gs
       :edges
       (map second)))

(defn has-elements?
  [{:keys [vertices edges] :as gs}]
  (or (seq edges) (seq vertices)))

(defn make-default-graph-settings []
  {:vis-map {:nodes {:id "_id"
                     :label "name"}
             :edges {:id "_id"
                     :label "_label"
                     :from "_outV"
                     :to "_inV"}}})

(defn add-vertex
  "Adds vertex `v` to the collection of loaded
   vertices under the identifier `id`."
  [id v gs]
  (update gs :vertices
          #(assoc %1 id v)))

(defn add-vertices
  "Adds the given list of vertices to the
   collection of loaded vertices"
  [gs id v & rest]
  (let [ret (add-vertex id v gs)]
    (if rest
      (if (next rest)
        (recur ret
               (first rest) (second rest)
               (nnext rest))
        (throw (js/Error. "add-vertices expects even number of arguments after graph state, found odd number")))
      ret)))

(defn add-edge
  "Adds edge `e` to the collection of loaded
   edges under the identifier `id`."
  [id e gs]
  (update gs :edges
          #(assoc %1 id e)))

(defn get-settings-node-map
  "Returns vertices' visualization map."
  [gs]
  (-> gs :settings :vis-map :nodes))

(defn get-settings-edge-map
  "Returns edges' visualization map."
  [gs]
  (-> gs :settings :vis-map :edges))
