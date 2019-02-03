(ns websocket-layer.network
  (:require [clojure.core.async :as async]
            [ring.adapter.jetty9.websocket :as jws]
            [websocket-layer.core :as napi]
            [missing.core :as miss]
            [websocket-layer.encodings :as enc])
  (:import (java.io ByteArrayInputStream)
           (java.util UUID)
           (clojure.core.async.impl.channels ManyToManyChannel)))

(def ^:dynamic *encoder*)
(def ^:dynamic *decoder*)
(def ^:dynamic *exception-handler*)

(defn on-channel-close [^ManyToManyChannel chan f]
  (add-watch
    (.closed chan)
    (UUID/randomUUID)
    (fn [_ _ old-state new-state]
      (when (and (not old-state) new-state)
        (miss/quietly (f)))))
  chan)

(defn send-message! [ws data]
  (let [finished (async/promise-chan)]
    (jws/send! ws
      (*encoder* data)
      {:write-failed
       (fn [e]
         (try
           (*exception-handler* e)
           (finally
             (async/close! finished))))
       :write-success
       (fn [] (async/close! finished))})
    finished))

(defn on-connect [ws]
  (let [outbound (napi/get-outbound)
        sender   (binding-conveyor-fn send-message!)]
    (async/go-loop []
      (when-some [msg (async/<! outbound)]
        (async/<! (sender ws msg))
        (recur))
      (let [{:keys [id]} (swap! napi/*state* assoc :socket ws)]
        (swap! napi/sockets assoc id napi/*state*)))))

(defn on-error [ws e]
  (*exception-handler* e))

(defn on-close [ws status reason]
  ; remove visibility of any ongoing activities
  (let [[{:keys [id outbound subscriptions]}] (reset-vals! napi/*state* {})]

    (swap! napi/sockets dissoc id)

    (miss/quietly (async/close! outbound))

    (doseq [sub (vals subscriptions)]
      ; close all the open subscriptions
      (miss/quietly (async/close! sub)))))

(defn on-command [ws command]
  (let [closure napi/*state*
        topic   (get command :id)
        proto   (keyword (get command :proto))
        {:keys [outbound subscriptions]} (deref closure)]

    (case proto

      :request
      (let [response (napi/handle-request (:data command))]
        (async/put! outbound {:data response :proto proto :id topic}))

      :subscription
      (cond

        (true? (get command :close))
        (when-some [sub (get subscriptions topic)]
          (async/close! sub))

        (contains? subscriptions topic)
        nil

        :otherwise
        (when-some [response (napi/handle-subscription (:data command))]
          (on-channel-close response (fn [] (swap! closure miss/dissoc-in [:subscriptions topic])))
          (swap! closure assoc-in [:subscriptions topic] response)
          (async/go-loop []
            (if-some [res (async/<! response)]
              (if (async/>! outbound {:data res :proto proto :id topic})
                (recur)
                (async/close! response))
              (async/>! outbound {:proto proto :id topic :close true})))))

      :push
      (napi/handle-push (:data command)))))

(defn on-text [ws message]
  (on-command ws (with-open [stream (ByteArrayInputStream. (.getBytes message))]
                   (*decoder* stream))))

(defn on-bytes [ws bites offset len]
  (let [buffer (byte-array len)
        _      (System/arraycopy bites offset buffer 0 len)]
    (on-command ws (with-open [stream (ByteArrayInputStream. buffer)]
                     (*decoder* stream)))))

(defn websocket-handler
  [{:keys [exception-handler encoding encoder decoder]
    :or   {encoding          :json
           exception-handler (fn [^Exception exception]
                               (.printStackTrace exception))}}]
  (let [encoder (or encoder (get-in enc/encodings [encoding :encoder]))
        decoder (or decoder (get-in enc/encodings [encoding :decoder]))]
    (fn [upgrade-request]
      (letfn
        [(mw [state handler]
           (fn [& args]
             (binding
               [napi/*state*        state
                *encoder*           encoder
                *decoder*           decoder
                *exception-handler* exception-handler]
               (try
                 (apply handler args)
                 (catch Exception e
                   (*exception-handler* e))))))]
        (miss/map-vals
          (binding-conveyor-fn
            (partial mw (atom (napi/new-state upgrade-request))))
          {:on-connect on-connect
           :on-error   on-error
           :on-close   on-close
           :on-text    on-text
           :on-bytes   on-bytes})))))

