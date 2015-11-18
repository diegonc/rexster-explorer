(ns ^:figwheel-always rexster-explorer.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [<!]]
            [rexster-explorer.http-rexster-graph :as rexster]
            [rexster-explorer.rexster-graph :as rg]))

(enable-console-print!)

(defonce app-state
  (atom {:current-graph "tinkergraph"
         "tinkergraph" {:graph (rexster/make-graph "192.168.1.6:8182" "tinkergraph")}}))

(defcomponent graph-information [data owner]
  (render [_]
    (let [graph (-> data :current-graph data :graph)
          graph-name (rexster/get-graph-name graph)
          graph-uri (rexster/get-graph-uri graph)]
      (dom/div
       (dom/h1 graph-name)
       (dom/h6 graph-uri)))))

(om/root
  graph-information
  app-state
  {:target (. js/document (getElementById "graph-info"))})
