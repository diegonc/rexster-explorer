(ns rexster-explorer.rexster-graph)

(defprotocol RexsterGraph
  (get-vertex [this id])
  (get-edge [this id])
  (get-both-edges [this id])
  (get-neighbourhood [this id])
  ;; Gremlin Extension
  (exec-graph-query [this query]))
