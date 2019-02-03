(defproject org.clojars.rutledgepaulv/websocket-layer "0.1.2-SNAPSHOT"

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
   [org.clojure/core.async "0.4.490"]
   [info.sunng/ring-jetty9-adapter "0.12.2"]
   [cheshire "5.8.1"]
   [com.cognitect/transit-clj "0.8.313"]]

  :profiles
  {:test
   {:dependencies
    [[org.eclipse.jetty.websocket/websocket-client "9.4.14.v20181114"]
     [stylefruits/gniazdo "1.1.1"]]}})
