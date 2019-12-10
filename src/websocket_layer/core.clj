(ns websocket-layer.core
  (:require [clojure.core.async :as async])
  (:import (java.util UUID)))

(def ^:dynamic *state* nil)
(defonce sockets (atom {}))

(defn new-state [request]
  {:id            (UUID/randomUUID)
   :socket        nil
   :outbound      (async/chan 100)
   :request       request
   :subscriptions {}
   :state         {}})

(defn get-socket-id []
  (some-> *state* deref :id))

(defn get-socket []
  (some-> *state* deref :socket))

(defn get-subscriptions []
  (some-> *state* deref :subscriptions))

(defn get-outbound []
  (some-> *state* deref :outbound))

(defn get-state []
  (some-> *state* deref :state))

(defn swap-state! [f & args]
  (get (swap! *state* update :state #(apply f % args)) :state))

(defn get-request []
  (some-> *state* deref :request))

(def dispatch (comp keyword :kind))
(defmulti handle-push dispatch)
(defmulti handle-request dispatch)
(defmulti handle-subscription dispatch)
