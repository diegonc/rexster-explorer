(ns ^:figwheel-always rexster-explorer.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [clojure.string :refer [blank?]]
            [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [cljs.core.async :refer [<! >! chan]]
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
                     (Default: \"Search Graph Context\")

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
                   :legend "Search Graph Context"
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

(defcomponent search-results
  "Renders the list of search results. The initial state
   must contain the following fields:
     - :events-chan Channel where generated events are placed
  "
  [data owner]
  (render-state
   [_ state]
   (let [results (:results data)]
     (if (empty? results)
       (dom/p "No results found for the given query.")
       (dom/ul
        (->> results
             (remove nil?)
             (map str)
             (map dom/li)))))))

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

(defmulti graph-query-op-build
  "Takes an event received from the graph-query's event channel
   and builds an op suitable for the graph-query-op-dispatch
   multi method."
  (fn [event owner graph] (:event-type event)))

;; Process a :search-box-enter-query event by building
;; an :exec-graph-query op which will execute the query
;; requested by the event.
(defmethod graph-query-op-build :search-box-enter-query
  [op owner graph]
  {:op-type :exec-graph-query
   :query (str "g." (:data op))
   :graph graph})

(defcomponent graph-query [data owner]
  (init-state [_] {:events-chan (chan)
                   :with-button false
                   :query-state {:success :empty}})
  (will-mount
   [_]
   (let [events-chan (om/get-state owner :events-chan)]
     (go (loop []
           (let [event (<! events-chan)
                 graph (-> data :current-graph data :graph)]
             (-> event
                 (graph-query-op-build owner graph)
                 (graph-query-op-dispatch owner)))
           (recur)))))
  (render-state
   [_ state]
   (let [current-graph (-> data :current-graph data)
         results-class (if (:with-button state)
                         "search-results with-button"
                         "search-results")]
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
