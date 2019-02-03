(ns websocket-layer.helpers
  (:require [websocket-layer.network :as net]
            [ring.adapter.jetty9 :as jetty]
            [gniazdo.core :as ws]
            [websocket-layer.core :as wl]
            [clojure.core.async :as async]
            [clojure.edn :as edn])
  (:import (clojure.lang MultiFn)))


(def ring-opts
  {:port                 3000
   :join?                false
   :async?               true
   :allow-null-path-info true
   :websockets           {"/ws" (net/websocket-handler {:encoding :edn})}})

(defn create-server []
  (letfn [(handler [request respond raise] (respond {:status 404}))]
    (jetty/run-jetty (fn [& args] (apply handler args)) ring-opts)))

(defprotocol Client
  (send-> [this msg])
  (<-receive [this])
  (close [this]))

(defn create-client []
  (let [uri     (str "ws://localhost:" (get ring-opts :port) "/ws")
        inbound (async/chan 100)
        client  (ws/connect uri :on-receive (fn [msg] (async/>!! inbound msg)))]
    (reify Client
      (send-> [this msg]
        (ws/send-msg client (pr-str msg)))
      (<-receive [this]
        (edn/read-string (async/<!! inbound)))
      (close [this]
        (ws/close client)))))

(def ^:dynamic *client*)
(def ^:dynamic *server*)

(defn fixture [tests]
  (binding [*server* (create-server)]
    (binding [*client* (create-client)]
      (try
        (tests)
        (finally
          (close *client*)
          (.stop *server*)
          (.reset ^MultiFn wl/handle-push)
          (.reset ^MultiFn wl/handle-request)
          (.reset ^MultiFn wl/handle-subscription))))))

(defn closed? [chan]
  @(.closed chan))