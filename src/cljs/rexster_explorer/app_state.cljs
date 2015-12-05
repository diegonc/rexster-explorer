(ns ^:figwheel-always rexster-explorer.app-state
  (:require [rexster-explorer.graph-state :as gs]
            [rexster-explorer.visualization :as vis]))

;; Module that provides a centralized interface to
;; the application state.
;;
;; The application state consists of the following
;; piecies of information:
;;
;;   - `:available-graphs` Collection of graph states
;;                         of all the graphs available
;;                         for visualization.
;;
;;   - `:current-graph`    Identifies the graph which is
;;                         currently being visualized.
;;
;;   - `:visualization`    The state of the visualization
;;                         as defined by the namespace
;;                         `rexster-explorer.visualization`.
;;
;;   - `:new-graph-settings` The settings of new graphs.
;;                           Used as a target for values
;;                           during settings entry and as
;;                           the source of settings when
;;                           creation is requested.
;;

(defn- set-visualization
  "Returns a new app state with `vis` set as its
   visualization."
  [vis app]
  (assoc app :visualization vis))

(defn make-new-state
  ([] {:available-graphs {}
       :current-graph :none
       :new-graph-settings (gs/make-default-graph-settings)})
  ([node options]
   (let [state (make-new-state)
         visualization (vis/make-new-visualization node options)]
     (set-visualization visualization state))))

(defn setup-visualization
  "Creates an initial visualization attached to the
   DOM element `node` and options `options` and
   returns a new app state built from `app` with its
   visualization set to it."
  [node options app]
  (let [visualization (vis/make-new-visualization node options)]
    (assoc app :visualization visualization)))

(defn get-current-graph-state
  "Gets the graph state of the currently selected
   graph in the `app` app state."
  [{:keys [current-graph available-graphs] :as app}]
  (available-graphs current-graph))

(defn has-current-graph?
  "Predicate that determines whether the app state
   `app` has a currently selected graph."
  [app]
  (not (nil? (get-current-graph-state app))))

(defn refresh-visualization
  "Regenerates the visualization of the currently
   selected graph."
  [{:keys [visualization] :as app}]
  (let [graph (get-current-graph-state app)]
    (if (gs/has-elements? graph)
      (vis/visualize-graph visualization graph)
      (vis/load-initial-graph visualization))))

(defn contains-graph?
  "Determines if a graph with identifier
   `graph-id` exists in the available
    graphs of `app`."
  [graph-id app]
  (contains? (:available-graphs app) graph-id))

(defn activate-graph
  "Makes the currently selected graph the one
   that has the id `graph-id` and updates the
   visualization.

   Returns a new app state with the changes
   above."
  [graph-id app]
  (assert (or (= :none graph-id)
              (contains-graph? graph-id app)))
  (let [new-app (assoc app :current-graph graph-id)]
    (refresh-visualization new-app)
    new-app))

(defn get-available-graphs-keys
  "Returns the keys of all the available
   graphs in `app` state."
  [app]
  (->> app
       :available-graphs
       (map first)))

(defn get-new-graph-settings
  "Returns the settings to use for new
   graphs in the given `app` state."
  [app]
  (:new-graph-settings app))

(defn clear-new-graph-settings
  "Returns a new app state based on `app`
   with the new graph settings cleared."
  [app]
  (assoc app :new-graph-settings
         (gs/make-default-graph-settings)))

(defn add-graph
  "Adds the graph given in the `graph`
   argument under then identifier given
   in the `id` argument  to the `app`
   state."
  [id graph app]
  (assert (not (contains-graph? id app)))
  (update app :available-graphs
          #(conj %1 [id graph])))

(defn delete-graph
  "Deletes the graph with the identifier
   given in the `graph-id` argument from
   the app state `app`."
  [graph-id app]
  (assert (contains-graph? graph-id app))
  (update app :available-graphs
          #(dissoc %1 graph-id)))

(defn get-visualization
  "Gets the visualization of the given `app` state."
  [app]
  (:visualization app))

(defn is-current-graph?
  "Determines whether `graph-id` is the currently
   selected graph in `app`."
  [graph-id app]
  (= (:current-graph app) graph-id))

