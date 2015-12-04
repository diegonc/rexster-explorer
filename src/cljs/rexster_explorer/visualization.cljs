(ns ^:figwheel-always rexster-explorer.visualization
  (:require [rexster-explorer.graph-state :as gs]
            [cljsjs.vis]))

;; Module that centralizes access to the visualization
;; state.
;;
;; This state consist of the folling information:
;;
;;   - `:data`    js object holding the DataSet instances
;;                used by the visualization.
;;
;;   - `:network` Vis.js Network object that manages the
;;                graph visualization.
;;

(defn- load-introductory-graph [nodes edges]
  (.clear nodes)
  (.add nodes #js [#js {:id 1 :label "A"}
                   #js {:id 2 :label "B"}])
  (.clear edges)
  (.add edges #js {:id "e1"
                   :from 1
                   :to 2
                   :label "Pick a graph then search and add vertices or edges"}))

(defn- make-introductory-graph []
  (let [nodes (js/vis.DataSet.)
        edges (js/vis.DataSet.)]
    (load-introductory-graph nodes edges)
    #js {:nodes nodes
         :edges edges}))

(defn make-new-visualization
  "Creates a visualization loaded with an introductory
   graph and attached to the given `node` and configured
   by the given options."
  [node options]
  (let [data (make-introductory-graph)
        network (js/vis.Network.
                 node
                 data
                 (clj->js options))]
    {:data data :network network}))

(defn load-initial-graph
  "Loads the introductory graph into the `vis`
   viualization."
  [{:keys [data] :as vis}]
  (let [nodes (.-nodes data)
        edges (.-edges data)]
    (load-introductory-graph nodes edges)))

(defn- make-node [graph vertex]
  ;; TODO: use the graph settings
  {:id    (:_id  vertex)
   :label (:name vertex)})

(defn- make-edge [graph edge]
  ;; TODO: use graph settings
  {:id    (:_id    edge)
   :from  (:_outV  edge)
   :to    (:_inV   edge)
   :label (:_label edge)})

(defn add-vertex
  "Adds to the visualization `vis` the vertex
   given in the `vertex` argument using the mapping
   of the provided `graph`."
  [{:keys [data] :as vis} graph vertex]
  (let [nodes (.-nodes data)
        node (make-node graph vertex)]
    (.update nodes 
             (clj->js node))))

(defn add-edge
  "Adds to the visualization `vis` the edge
   given in the `edge` argument using the mapping
   of the provided `graph`."
  [{:keys [data] :as vis} graph edge]
  (let [edges (.-edges data)
        edge (make-edge graph edge)]
    (.update edges 
             (clj->js edge))))

(defn visualize-graph
  "Updates the visualization `vis` to the set of
   nodes and edges currently loaded on the graph
   `graph`."
  [{:keys [data network] :as vis} graph]
  (let [node-dataset (.-nodes data)
        edge-dataset (.-edges data)
        nodes (->> graph
                   gs/get-loaded-vertices
                   (map make-node graph))
        edges (->> graph
                   gs/get-loaded-edges
                   (map make-edge graph))]
    (.clear node-dataset)
    (.add node-dataset (clj->js nodes))
    (.clear edge-dataset)
    (.add edge-dataset (clj->js edges))))
