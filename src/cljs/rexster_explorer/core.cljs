(ns ^:figwheel-always rexster-explorer.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :refer [blank?]]
            [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [<! >! chan close!]]
            [rexster-explorer.http-rexster-graph :as rexster]
            [rexster-explorer.rexster-graph :as rg]
            [cljsjs.vis]
            [cljsjs.react-burger-menu]
            [cljsjs.react-sanfona]))

(enable-console-print!)

(defn- log [x] (println x) x)

(defonce vis-options (clj->js
                      {:layout {:randomSeed 994841}
                       :nodes {:shadow true
                               :shape :circle}
                       :edges {:arrows {:to {:enabled true
                                             :scaleFactor 0.745}}}}))

;; Build introductory graph
(defn build-introductory-graph [nodes edges]
    (.clear nodes)
    (.add nodes #js [#js {"id" 1 "label" "A"}
                     #js {"id" 2 "label" "B"}])
    (.clear edges)
    (.add edges #js {"id" "e1"
                     "from" 1
                     "to" 2
                     "label" "Search and add vertices or edges"}))

(defn vis-make-introductory-graph []
  (let [nodes (js/vis.DataSet.)
        edges (js/vis.DataSet.)]
    (build-introductory-graph nodes edges)
    #js {"nodes" nodes
         "edges" edges}))

(defonce vis-data (vis-make-introductory-graph))
(defonce vis-network (js/vis.Network.
                      (. js/document (getElementById "graph-render"))
                      vis-data
                      vis-options) )

(defn vis-make-node [rexster-vertex]
  ;; TODO: make this process a graph setting
  {:id (:_id rexster-vertex)
   :label (:name rexster-vertex)})

(defn vis-make-edge [rexster-edge]
  ;; TODO: make this process a graph setting
  {:id (:_id rexster-edge)
   :from (:_outV rexster-edge)
   :to (:_inV rexster-edge)
   :label (:_label rexster-edge)})

(defn vis-build-current-graph [graph vis-data]
  (let [node-dataset (.-nodes vis-data)
        edge-dataset (.-edges vis-data)
        nodes (->> graph
                   :vertices
                   (map second)
                   (map vis-make-node))
        edges (->> graph
                   :edges
                   (map second)
                   (map vis-make-edge))]
    (.clear node-dataset)
    (.add node-dataset (clj->js nodes))
    (.clear edge-dataset)
    (.add edge-dataset (clj->js edges))))

(defn vis-add-vertex [vis-data v]
  (.update (.-nodes vis-data)
           (clj->js (vis-make-node v))))

(defn vis-add-edge [vis-data e]
  (.update (.-edges vis-data)
           (clj->js (vis-make-edge e))))

(defonce app-state
  (atom {:current-graph "tinkergraph"
         :available-graphs
         {"tinkergraph" {:graph (rexster/make-graph "localhost:8182" "tinkergraph")
                         :vertices {}
                         :edges {}}
          "gratefulgraph"
          {:graph (rexster/make-graph "localhost:8182" "gratefulgraph")
           :vertices {}
           :edges {}}}}))

(defn get-current-graph-state [cursor]
  (let [current-graph (:current-graph cursor)
        available-graphs (:available-graphs cursor)]
    (available-graphs current-graph)))

(defn react-build [component props & children]
  (let [React (.-React js/window)]
    (.createElement React component
                    (clj->js props)
                    (to-array (flatten children)))))

(defn vis-reload-data [app-state]
  (let [graph (or (get-current-graph-state app-state) {})
        edges (:edges graph)
        nodes (:vertices graph)]
    (if (or (seq edges) (seq nodes))
      (vis-build-current-graph graph vis-data)
      (build-introductory-graph (.-nodes vis-data)
                                (.-edges vis-data)))))

(defcomponent graph-menu-content [data owner]
  (render
   [_]
   (let [Accordion (.-Accordion js/ReactSanfona)
         AccordionItem (.-AccordionItem js/ReactSanfona)
         available-graphs (map first (:available-graphs data))]
     (react-build
      Accordion {}
      (map
       #(react-build AccordionItem {:title %}
                     (dom/div "Item body"))
       available-graphs)))))

(defcomponent graph-menu [data owner]
  (render
   [_]
   (let [menu (.-slide js/BurgerMenu)]
     (react-build
      menu
      {:id "graph-menu"
       :outerContainerId "outer-container"
       :pageWrapId "page-wrap"}
      (dom/div
       {:class "menu-container"}
       (dom/h2 "Graphs List")
       (dom/div
        {:class "menu-content"}
        (om/build graph-menu-content data)))))))

(defcomponent graph-information [data owner]
  (render [_]
    (let [graph (:graph (get-current-graph-state data))
          graph-name (rexster/get-graph-name graph)
          graph-uri (rexster/get-graph-uri graph)]
      (dom/div
       {:class "graph-info"}
       (dom/div
        (dom/h1 graph-name)
        (dom/h6 graph-uri))))))

(defn search-box-submit-query
  "Puts the query, if set, in the events channel"
  [state]
  (if-not (blank? (:query state))
     (go
       (>! (:events-chan state)
           {:event-type :search-box-enter-query
            :data (:query state)}))))

(defn search-box-error-class
  "Returns the error class if 'e' is not nil"
  [e]
  (if (nil? e)
    ""
    " input-has-errors"))

(defcomponent search-box
  " An input box with a prefix label and an optional button to submit
    a query.

    This component requires that the following fields
    are set in its initial state:
      - :legend      Legend of the fieldset that contains the elements
                     of this component.
                     (Default: \"Search (Graph Context)\")

      - :prefix      The text to display in the prefix
                     area of the input box.
                     (Default: \"g.\")

      - :placeholder The placeholder of the input box displayed
                     when no query has been entered.
                     (Default: \"Gremlin query used to search the graph\")

      - :with-button Defines whether the \"Search\" button

      - :error       Error that is rendered. If set, it should be a map
                     containing the keys :message and/or :error.
                     (Default: nil)

      - :events-chan Channel where the query is placed when the \"Search\"
                     button or \"Enter\" key is pressed. The message put in
                     the channel has the following format:

                         { :event-type :search-box-enter-query
                           :data    #query string# }

                     where #query string# is the query entered by the user.
  "
  [current-graph owner]
  (init-state [_] {:prefix "g."
                   :placeholder "Gremlin query used to search the graph"
                   :legend "Search (Graph Context)"
                   :with-button true})
  (render-state
   [this state]
   (dom/fieldset {:class (if (:with-button state) "with-button")}
    (dom/legend (:legend state))
    (dom/div
     {:class (str "input-like search-input-container"
                  (search-box-error-class (:error state)))}
     (dom/input {:class "not-input search-prefix"
                 :type "text" :value (:prefix state)
                 :disabled true})
     (dom/input
      {:class "not-input search-text"
       :placeholder (:placeholder state)
       :type "text"
       :value (:query state)
       :on-change #(om/set-state! owner :query (.. % -target -value))
       :on-key-down #(when (= (.-key %) "Enter")
                      (search-box-submit-query state))}))
    (dom/div
     {:class "search-button-container"}
     (if (:with-button state)
       (dom/button
        {:type "button"
         :on-click #(search-box-submit-query state)}
        "Search"))))))

(defn search-result-element? [item]
  (and (map? item)
       (some #{"vertex" "edge"} [(:_type item)])))

(defn search-result-present? [graph item]
  (let [{:keys [edges vertices]} (om/value graph)]
    (condp = (:_type item)
          "vertex" (vertices (:_id item))
          "edge"   (edges (:_id item))
          false)))

(defn search-result-add-item [owner-state item]
  (let [chan (:events-chan owner-state)]
    (go (>! chan
            {:event-type :search-result-add-element
             :element-type (:_type item)
             :element-id (:_id item)}))))

(defcomponent search-result
  " Render a given search result.

    The parameter 'data' must be a map with the following
    fields:
      - :graph A cursor to the currently selected graph
      - :item  The item from the query result set that has
               to be rendered by this component.
      - :key   This field is not used by this component but
               should be used as a react key when calling
               om/build-all to display the result set using
               this component.

    The component requires that the initial state has the
    following fields:
      - :events-chan Channel where events generated by this
                     component are sent.
                     The possible events are:
                       * :search-result-add-element

                         Triggered when the Add control is
                         activated for an item that is identified
                         as a graph element to request its addition
                         to the set of displayed elements.

                         Message format:
                           { :event-type   :search-result-add-element
                             :element-type [\"vertex\" | \"edge\"]
                             :element-id   Id of the element
                           }
  "
  [{:keys [graph item] :as data} owner]
  (render-state
   [_ state]
   (dom/div
    {:class "row"}
    (if (and (search-result-element? item)
             (not (search-result-present? graph item)))
      [(dom/div {:class "ten columns"} (str item))
       (dom/div {:class "two columns"
                 :on-click #(search-result-add-item state item)}
                "Add")]
      (dom/div {:class "twelve columns"} (str item))))))

(defn search-results-make-item [index result]
  (if (map? result)
    {:key (:_id result) :item result}
    {:key index :item result}))

(defcomponent search-results
  "Renders the list of search results.

   `data` should be a map containing the following keys:
     - :graph   Cursor to the currently selected graph
     - :results Array of results to render

   The initial state must contain the following fields:
     - :events-chan Channel where generated events are placed
  "
  [data owner]
  (render-state
   [_ state]
   (let [current-graph (:graph data)
         results (->> data
                      :results
                      (remove nil?)
                      (map search-results-make-item (iterate inc 1))
                      (map #(assoc % :graph current-graph)))]
     (if (empty? results)
       (dom/div
        {:class "row"}
        (dom/p
         {:class "twelve columns"}
         "No results found for the given query."))
       (dom/div
        {:class "container with-no-padding"}
        (om/build-all search-result results
                      {:key :key
                       :init-state {:events-chan (:events-chan state)}}))))))

(defmulti graph-query-op-dispatch
  "Graph query op dispatcher."
  (fn [op owner] (:op-type op)))

;;    Runs a query against the graph and puts the results
;;    in the :results field of the owner's state.
;;
;;    Information about the executed query is placed in the
;;    :query-state field of the owner's state.
;;
;;      op is { :op-type :exec-graph-query
;;              :query   Query to execute
;;              :graph   Graph where the query is run
;;            }
(defmethod graph-query-op-dispatch :exec-graph-query
  [op owner]
  (let [query (:query op)
        graph (:graph op)]
    (go
      (let [resp (<! (rg/exec-graph-query graph query))
            success (:success resp)
            results (:results resp)]
        (if success
          (om/set-state! owner :results results))
        (om/update-state!
         owner :query-state
         (fn [x]
           (let [xn (assoc x :success success)]
             (if-not success
               (assoc xn
                      :message (:message results)
                      :error (:error results))
               (dissoc xn :message :error)))))))))

(defn graph-state-has-elements? [graph-state]
  (or (seq (:vertices graph-state))
      (seq (:edges graph-state))))

;;    Adds a graph element to the appropiate visualization's
;;    dataset.
;;
;;      op is { :op-type      :add-graph-element
;;              :element-type Type of the element to add
;;              :element-id   Id of the element to add
;;              :graph-state  Cursor to the currently selected
;;                            graph's state. The element to add
;;                            is looked up in the graph it
;;                            represents.
;;              :vis-dataset  The visualization's dataset where
;;                            a representation of the element
;;                            is added.
;;            }
(defmethod graph-query-op-dispatch :add-graph-element
  [op owner]
  (let [_type (:element-type op)
        _id (:element-id op)
        graph-state (:graph-state op)
        graph (:graph graph-state)
        vis-data (:vis-dataset op)]
    (condp = _type
      "vertex"
      (go (let [{v? :success v :results} (<! (rg/get-vertex graph _id))]
            (if v?
              (let [rebuild? (not (graph-state-has-elements? graph-state))]
                (om/transact! graph-state :vertices #(assoc % _id v))
                (if rebuild?
                  (vis-build-current-graph @graph-state vis-data)
                  (vis-add-vertex vis-data v)))
              ;; TODO: handle errors
              (:message v))))
      "edge"
      (go (let [{e? :success e :results} (<! (rg/get-edge graph _id))
                {o? :success o :results} (<! (rg/get-vertex graph (:_outV e)))
                {i? :success i :results} (<! (rg/get-vertex graph (:_inV e)))]
            (if (and e? o? i?)
              (let [rebuild? (not (graph-state-has-elements? graph-state))]
                (om/transact! graph-state :vertices
                              #(assoc % (:_id o) o (:_id i) i))
                (om/transact! graph-state :edges #(assoc % _id e))
                (if rebuild?
                  (vis-build-current-graph @graph-state vis-data)
                  (do
                    (vis-add-vertex vis-data o)
                    (vis-add-vertex vis-data i)
                    (vis-add-edge vis-data e))))
              ;; TODO: handle errors
              (map :message [e o i])))))))

(defmulti graph-query-op-build
  "Takes an event received from the graph-query's event channel
   and builds an op suitable for the graph-query-op-dispatch
   multi method."
  (fn [event owner graph-state] (:event-type event)))

;; Process a :search-box-enter-query event by building
;; an :exec-graph-query op which will execute the query
;; requested by the event.
(defmethod graph-query-op-build :search-box-enter-query
  [op owner graph-state]
  {:op-type :exec-graph-query
   :query (str "g." (:data op))
   :graph (:graph graph-state)})

;; Process a :search-result-add-element event by
;; building an :add-graph-element op which will
;; update the visualization.
(defmethod graph-query-op-build :search-result-add-element
  [op owner graph-state]
  {:op-type      :add-graph-element
   :element-type (:element-type op)
   :element-id   (:element-id op)
   :graph-state  graph-state
   :vis-dataset  vis-data})

(defcomponent graph-query [data owner]
  (init-state [_] {:events-chan (chan)
                   :term-chan   (chan)
                   :with-button false
                   :query-state {:success :empty}})
  (will-mount
   [_]
   (let [events-chan (om/get-state owner :events-chan)
         term-chan   (om/get-state owner :term-chan)
         channels    [events-chan term-chan]]
     (go (loop []
           (let [[msg channel] (alts! channels)]
             (if (= term-chan channel)
               (map close! channels)
               (let [graph (get-current-graph-state data)]
                 (-> msg
                     (graph-query-op-build owner graph)
                     (graph-query-op-dispatch owner))
                 (recur))))))))
  (will-unmount
   [_]
   (go (>! (om/get-state owner :term-chan) "graph-query: done")))
  (render-state
   [_ state]
   (let [current-graph (get-current-graph-state data)
         results-class (str "search-results"
                            (if (:with-button state)
                              " with-button"))]
     (dom/div
      {:class "search"}
      (om/build search-box current-graph
                {:init-state {:events-chan (:events-chan state)
                              :prefix "g."
                              :with-button (:with-button state)}
                 :state {:error (if-not (-> state :query-state :success)
                                  (select-keys (:query-state state)
                                               [:message :error]))}})
      (dom/div
       {:class results-class}
       (let [success (-> state :query-state :success)]
         (if-not success
           (dom/div {:class "search-error-message"}
                    (dom/p (-> state :query-state :message))
                    (dom/pre (-> state :query-state :error)))
           (if-not (= :empty success)
             (om/build search-results {:graph current-graph
                                       :results (-> state :results)}
                       {:init-state {:events-chan (:events-chan state)}})))))))))

;; Attach burger menu
(om/root
 graph-menu
 app-state
 {:target (. js/document (getElementById "burger-menu"))})

;; Attach graph information component
(om/root
 graph-information
 app-state
 {:target (. js/document (getElementById "graph-info"))})

;; Attach graph query component
(om/root
  graph-query
  app-state
  {:target (. js/document (getElementById "graph-search"))})

(defn on-jsload [& args]
  (let [state @app-state]
    (vis-reload-data state)))
