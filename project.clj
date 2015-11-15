(defproject rexster-explorer "0.1.0-SNAPSHOT"
  :description "A graphical interface to Rexster graphs"
  :url "http://github.com/diegonc/rexster-explorer"
  :license {:name "GNU General Public License, version 3"
            :url "http://opensource.org/licenses/GPL-3.0"}

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.122"]
                 [org.clojure/core.async "0.2.371"]
                 [org.omcljs/om "0.9.0"]
                 [prismatic/om-tools "0.3.12"]
                 [com.cemerick/url "0.1.1"]
                 [cljs-http "0.1.37"]
                 [clj-http "2.0.0"]
                 [compojure "1.4.0"]
                 [ring "1.4.0"]]

  :plugins [[lein-cljsbuild "1.1.0"]
            [lein-figwheel "0.4.1"]]

  :source-paths ["src/clj"]
  :main rexster-explorer.server
  :hooks [leiningen.cljsbuild]

  :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                    "target"]

  :profiles {:uberjar {:aot :all}}

  :cljsbuild {
    :builds [{:id "dev"
              :source-paths ["src/cljs"]
              :jar true
              :figwheel {:websocket-host :js-client-host
;                         :on-jsload "rexster-explorer.core/on-js-reload"
                        }
              :compiler {:main rexster-explorer.core
                         :asset-path "js/compiled/out"
                         :output-to "resources/public/js/compiled/rexster_explorer.js"
                         :output-dir "resources/public/js/compiled/out"
                         :source-map-timestamp true}}
             {:id "min"
              :source-paths ["src/cljs"]
              :jar true
              :compiler {:main rexster-explorer.core
                         :output-to "resources/public/js/compiled/rexster_explorer.js"
                         :optimizations :advanced
                         :pretty-print false
                         :source-map-timestamp true}}]}
  :figwheel {:css-dirs ["resources/public/css"]
             :ring-handler rexster-explorer.server/graph-proxy})
