(defproject org.clojars.rutledgepaulv/websocket-layer "0.1.3-SNAPSHOT"

  :description
  "A layer of glue for jetty and core.async"

  :url
  "https://github.com/rutledgepaulv/websocket-layer"

  :license
  {:name "MIT" :url "http://opensource.org/licenses/MIT"}

  :deploy-repositories
  [["releases" :clojars]
   ["snapshots" :clojars]]

  :dependencies
  [[org.clojure/clojure "1.10.0"]
   [org.clojure/core.async "0.6.532"]
   [info.sunng/ring-jetty9-adapter "0.12.5"]
   [metosin/jsonista "0.2.5"]
   [com.cognitect/transit-clj "0.8.319"]]

  :profiles
  {:test
   {:dependencies
    [[org.eclipse.jetty.websocket/websocket-client "9.4.20.v20190813"]
     [stylefruits/gniazdo "1.1.2"]]}})
