(ns ^:figwheel-always rexster-explorer.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :refer [blank?]]
            [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [<! >! chan close!]]
            [rexster-explorer.http-rexster-graph :as rexster]
            [rexster-explorer.rexster-graph :as rg]
            [rexster-explorer.app-state :as as]
            [rexster-explorer.graph-state :as gs]
            [rexster-explorer.visualization :as vis]
            [cljsjs.vis]
            [cljsjs.react-burger-menu]
            [cljsjs.react-sanfona]))

(enable-console-print!)

(defn- log [x & tag]
  (.log js/console (apply str tag) x) x)

(defonce vis-options
  (clj->js
   {:layout {:randomSeed 994841}
    :nodes {:shadow true
            :shape :dot}
    :edges {:arrows {:to {:enabled true
                          :scaleFactor 0.745}}}}))

(defonce app-state (atom (as/make-new-state)))

(defn react-build [component props & children]
  (let [React (.-React js/window)]
    (.createElement React component
                    (clj->js props)
                    (to-array (flatten children)))))

(defn activate-graph [cursor graph]
  (om/transact! cursor []
                #(as/activate-graph graph %)))

(defn delete-graph [cursor graph]
  (when (as/is-current-graph? graph cursor)
    (activate-graph cursor :none))
  (om/transact! cursor []
                #(as/delete-graph graph %)))

(defcomponent editable-setting
  " An editable setting.

    `data` must be a map with the following
    fields:
      - `:key-desc` description of the key to edit
                    that must be a map with the
                    following fields:
                    - `:key`   key of the cursor
                               edit
                    - `:label` label to display
                               [optional]
      - `:cursor` cursor pointing to the settings
                  being edited

    The `readonly?` option determines whether
    this component is editable or not.
    The `save-cb` option is a callback invoked
    when the edited value is confirmed.
  "
  [data owner {:keys [readonly? save-cb]}]
  (render
   [_]
   (let [edited-key (-> data :key-desc :key)
         label (-> data :key-desc :label)
         cursor (:cursor data)]
     (dom/div
      {:class "row"}
      (dom/div
       {:class "four columns"
        :style {:text-align "right"}}
       (dom/label (or label (str edited-key))))
      (dom/div
       {:class "eight columns"}
       (dom/input
        {:style {:padding-left "5px"
                 :width "100%"
                 :font-family "'Cutive Mono', monospace, sans"}
         :read-only readonly?
         :value (edited-key cursor)
         :on-change #(om/transact!
                      cursor edited-key
                      (fn [_] (.. % -target -value)))
         :on-key-down #(when (= "Enter" (.-key %))
                         (if (ifn? save-cb)
                           (save-cb edited-key (edited-key cursor))))}))))))

(defcomponent graph-settings-form
  " Presents an editable view of the
    settings of a given graph.

    `data` must be a cursor to the graph's
    settings, structured as described in
    `rexster-explorer.graph-state` namespace.

    The `new?` option, if present, indicates
    whether the settings being edited comes
    from a new graph; `:host-port` and `:name`
    fields are editable only in that case.

    `on-save` is a callback invoked when the
    user request to save the changes. Each time
    the user confirms the edition of a field the
    callback is called with the modified `key` and
    `new value` as arguments.
  "
  [data owner {:keys [new? on-save]}]
  (render
   [_]
   (let [save-cb #(if (ifn? on-save)
                    (on-save %1 %2))]
     (dom/div
      {:class "container with-no-padding"
       :style {:padding "10px 10px"}}
      (om/build editable-setting
                {:key-desc {:key :host-port}
                 :cursor data}
                {:opts {:readonly? (not new?)
                        :save-cb save-cb}})
      (om/build editable-setting
                {:key-desc {:key :name}
                 :cursor data}
                {:opts {:readonly? (not new?)
                        :save-cb save-cb}})
      (om/build editable-setting
                {:key-desc {:key :id
                            :label ":vertex-id"}
                 ;; TODO add function to graph-state?
                 :cursor (-> data :vis-map :nodes)}
                {:opts {:save-cb save-cb}})
      (om/build editable-setting
                {:key-desc {:key :label
                            :label ":vertex-label"}
                 ;; TODO add function to graph-state?
                 :cursor (-> data :vis-map :nodes)}
                {:opts {:save-cb save-cb}})
      (om/build editable-setting
                {:key-desc {:key :id
                            :label ":edge-id"}
                 ;; TODO add function to graph-state?
                 :cursor (-> data :vis-map :edges)}
                {:opts {:save-cb save-cb}})
      (om/build editable-setting
                {:key-desc {:key :label
                            :label ":edge-label"}
                 ;; TODO add function to graph-state?
                 :cursor (-> data :vis-map :edges)}
                {:opts {:save-cb save-cb}})
      (om/build editable-setting
                {:key-desc {:key :from
                            :label ":edge-from"}
                 ;; TODO add function to graph-state?
                 :cursor (-> data :vis-map :edges)}
                {:opts {:save-cb save-cb}})
      (om/build editable-setting
                {:key-desc {:key :to
                            :label ":edge-to"}
                 ;; TODO add function to graph-state?
                 :cursor (-> data :vis-map :edges)}
                {:opts {:save-cb save-cb}})))))

(defcomponent graph-menu-item [data owner]
  (render
   [_]
   (dom/div
    {:class "menu-item"}
    (dom/div
     {:class "settings"})
    (dom/div
     {:class "actions"}
     (dom/button
      {:type "button"
       :class "delete"
       :on-click #(delete-graph (:cursor data) (:item data))}
      "Delete")
     (dom/button
      {:type "button"
       :class "activate"
       :on-click #(activate-graph (:cursor data) (:item data))}
      "Activate")))))

(defn make-new-graph-state
  [{:keys [host-port name] :as settings}]
  (let [settings (gs/keywordize-settings settings)]
    (->> (gs/make-new-state)
         (gs/set-graph (rexster/make-graph host-port name))
         (gs/set-settings settings))))

(defcomponent graph-menu-content [data owner]
  (init-state [_] {:reset-selected true})
  (render-state
   [_ {:keys [reset-selected] :as state}]
   (let [Accordion (.-Accordion js/ReactSanfona)
         AccordionItem (.-AccordionItem js/ReactSanfona)
         available-graphs (as/get-available-graphs-keys data)
         new-graph-cursor (as/get-new-graph-settings data)]
     (dom/div
      (dom/div
       {:class "new-graph"}
       (react-build
        Accordion {:selectedIndex -1 ;; select none please
                   :resetSelected reset-selected
                   :onItemActivation #(om/set-state!
                                       owner
                                       :reset-selected
                                       false)}
        (react-build
         AccordionItem
         {:key 0 ;; silent React warning
          :title "New Graph"}
         (om/build graph-settings-form
                   new-graph-cursor
                   {:react-key 0 ;; silent React warning
                    :opts {:new? true}})
         (dom/div
          {:key 1 ;; silent React warning
           :style {:text-align "right"
                   :padding-right "10px"}}
          (dom/button
           {:type "button"
            :on-click #(when-not (as/contains-graph?
                                  (:name new-graph-cursor) data)
                         ;; TODO: validation...
                         (let [name (:name new-graph-cursor)
                               state (make-new-graph-state
                                      (om/value new-graph-cursor))]
                           (om/transact!
                            data []
                            (fn [app]
                              (->> app
                                   (as/add-graph name state)
                                   as/clear-new-graph-settings)))
                           (om/set-state!
                            owner :reset-selected true)))}
           "Create")))))
      (dom/div
       {:class "existing-graphs"}
       (react-build
        Accordion {}
        (map
         #(react-build AccordionItem {:key % ;; silent React warning
                                      :title %}
                       (om/build graph-menu-item
                                 {:cursor data
                                  :item %}
                                 {:react-key 0}))
         available-graphs)))))))

(defcomponent graph-menu [data owner]
  (render
   [_]
   (let [menu (.-slide js/BurgerMenu)]
     (react-build
      menu
      {:id "graph-menu"
       :outerContainerId "outer-container"
       :pageWrapId "page-wrap"
       :initiallyOpened (not (as/has-current-graph? data))}
      (dom/div
       {:key 0 ;; silent React warning
        :class "menu-container"}
       (dom/h2 "Graphs List")
       (dom/div
        {:class "menu-content"}
        (om/build graph-menu-content data)))))))

(defcomponent graph-information [data owner]
  (render
   [_]
   (let [graph (:graph data) ;; TODO gs/*
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
  ;; TODO: make it use the modules...
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

;;    Adds a graph element to the appropiate visualization's
;;    dataset.
;;
;;      op is { :op-type       :add-graph-element
;;              :element-type  Type of the element to add
;;              :element-id    Id of the element to add
;;              :graph-state   Cursor to the currently selected
;;                             graph's state. The element to add
;;                             is looked up in the graph it
;;                             represents.
;;              :visualization The visualization where a
;;                             representation of the element
;;                             is added.
;;            }
(defmethod graph-query-op-dispatch :add-graph-element
  [op owner]
  (let [_type (:element-type op)
        _id (:element-id op)
        graph-state (:graph-state op)
        graph (:graph graph-state) ;; TODO: use module?
        visualization (:visualization op)]
    (condp = _type
      "vertex"
      (go (let [{v? :success v :results} (<! (rg/get-vertex graph _id))]
            (if v?
              (let [rebuild? (not (gs/has-elements? graph-state))]
                (om/transact! graph-state []
                              #(gs/add-vertex _id v %1))
                ;; TODO: use invisible component to
                ;; rebuild/update visualization?
                (if rebuild?
                  (vis/visualize-graph visualization @graph-state)
                  (vis/add-vertex visualization @graph-state v)))
              ;; TODO: handle errors
              (:message v))))
      "edge"
      (go (let [{e? :success e :results} (<! (rg/get-edge graph _id))
                {o? :success o :results} (<! (rg/get-vertex graph (:_outV e)))
                {i? :success i :results} (<! (rg/get-vertex graph (:_inV e)))]
            (if (and e? o? i?)
              (let [rebuild? (not (gs/has-elements? graph-state))]
                (om/transact! graph-state []
                              #(gs/add-vertices %1
                                                (:_id o) o
                                                (:_id i) i))
                (om/transact! graph-state []
                              #(gs/add-edge _id e %1))
                (if rebuild?
                  (vis/visualize-graph visualization @graph-state)
                  (do
                    (vis/add-vertex visualization @graph-state o)
                    (vis/add-vertex visualization @graph-state i)
                    (vis/add-edge visualization @graph-state e))))
              ;; TODO: handle errors
              (map :message [e o i])))))))

(defmulti graph-query-op-build
  "Takes an event received from the graph-query's event channel
   and builds an op suitable for the graph-query-op-dispatch
   multi method."
  (fn [event owner app-state] (:event-type event)))

;; Process a :search-box-enter-query event by building
;; an :exec-graph-query op which will execute the query
;; requested by the event.
(defmethod graph-query-op-build :search-box-enter-query
  [op owner app-state]
  {:op-type :exec-graph-query
   :query (str "g." (:data op))
   :graph (:graph (as/get-current-graph-state app-state))})

;; Process a :search-result-add-element event by
;; building an :add-graph-element op which will
;; update the visualization.
(defmethod graph-query-op-build :search-result-add-element
  [op owner app-state]
  {:op-type      :add-graph-element
   :element-type (:element-type op)
   :element-id   (:element-id op)
   :graph-state  (as/get-current-graph-state app-state)
   :visualization (om/value (as/get-visualization app-state))})

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
               (let [root-cursor (om/root-cursor app-state)]
                 (-> msg
                     (graph-query-op-build owner root-cursor)
                     (graph-query-op-dispatch owner))
                 (recur))))))))
  (will-unmount
   [_]
   (go (>! (om/get-state owner :term-chan) "graph-query: done")))
  (will-receive-props
   [_ next-props]
   (let [previous-props (om/get-props owner)
         ;; TODO: use modules to acces data...
         previous-graph (:current-graph previous-props)
         next-graph (:current-graph next-props)]
     (when-not (= previous-graph next-graph)
       (om/set-state! owner :query-state {:success :empty}))))
  (render-state
   [_ state]
   (let [current-graph (as/get-current-graph-state data)
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

;; Output visualization root element
(defcomponent graph-visualization [root-cursor owner]
  (did-mount
   [_]
   (let [node (om/get-node owner)]
     (om/transact! root-cursor []
                   #(as/setup-visualization
                     node vis-options %1))))
  (render-state
   [_ state]
   (dom/div {:id (:id state)
             :class (:class state)})))

;; Build page layout
(defcomponent root-component [root-cursor owner]
  (render
   [_]
   (dom/div
    {:id "outer-container"}
    (dom/div {:id "burger-menu"}
             (om/build graph-menu root-cursor))
    (dom/div
     {:id "page-wrap" :class "container"}
     (dom/div
      {:class "row header"}
      (dom/div {:id "graph-info"
                :class "twelve columns"}
               (if (as/has-current-graph? root-cursor)
                 (om/build graph-information
                           (as/get-current-graph-state root-cursor)))))
     (dom/div
      {:class "row content"}
      (dom/div {:id "graph-search"
                :class "three columns search"}
               (if (as/has-current-graph? root-cursor)
                 (om/build graph-query root-cursor)))
      (om/build graph-visualization root-cursor
                {:init-state
                 {:id "graph-render"
                  :class "nine columns"}}))))))

;; HACK Patch react-burger-menu
(set! (.-slide js/BurgerMenu)
      (js/patch_initially_opened_prop (.-slide js/BurgerMenu)))
;; HACK Patch sanfona accordion
(set! (.-Accordion js/ReactSanfona)
      (js/patch_enhance_sanfona_accordion
       (.-Accordion js/ReactSanfona)))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Attach root component
(om/root
 root-component
 app-state
 {:target (. js/document (getElementById "app"))})

(defn on-jsload [& args]
  (let [state @app-state]
    (as/refresh-visualization state)))
