(ns websocket-layer.network
  (:require [clojure.core.async :as async]
            [ring.adapter.jetty9.websocket :as jws]
            [websocket-layer.core :as wl]
            [websocket-layer.encodings :as enc])
  (:import (java.io ByteArrayInputStream)))

(def ^:dynamic *encoder*)
(def ^:dynamic *decoder*)
(def ^:dynamic *exception-handler*)

(defmacro quietly
  "Execute the body and return nil if there was an error"
  [& body]
  `(try ~@body (catch Throwable _# nil)))

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
  (let [outbound (wl/get-outbound)
        sender   (bound-fn* send-message!)]
    (async/go-loop []
      (when-some [msg (async/<! outbound)]
        (async/<! (sender ws msg))
        (recur)))
    (let [{:keys [id]} (swap! wl/*state* assoc :socket ws)]
      (swap! wl/sockets assoc id wl/*state*))))

(defn on-error [_ e]
  (*exception-handler* e))

(defn on-close [_ _ _]
  ; remove visibility of any ongoing activities
  (let [[{:keys [id outbound subscriptions]}] (reset-vals! wl/*state* {})]

    (swap! wl/sockets dissoc id)

    (quietly (async/close! outbound))

    (doseq [sub (vals subscriptions)]
      ; close all the open subscriptions
      (quietly (async/close! sub)))))

(defn on-command [_ command]
  (let [closure wl/*state*
        topic   (get command :id)
        proto   (keyword (get command :proto))
        {:keys [outbound subscriptions]} (deref closure)]

    (case proto

      :request
      (let [response (wl/handle-request (:data command))]
        (async/put! outbound {:data response :proto proto :id topic}))

      :subscription
      (cond

        (true? (get command :close))
        (when-some [sub (get subscriptions topic)]
          (async/close! sub))

        (contains? subscriptions topic)
        nil

        :otherwise
        (when-some [response (wl/handle-subscription (:data command))]
          (wl/on-chan-close response (fn [] (swap! closure update :subscriptions dissoc topic)))
          (swap! closure assoc-in [:subscriptions topic] response)
          (async/go-loop []
            (if-some [res (async/<! response)]
              (if (async/>! outbound {:data res :proto proto :id topic})
                (recur)
                (async/close! response))
              (async/>! outbound {:proto proto :id topic :close true})))))

      :push
      (wl/handle-push (:data command)))))

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
               [wl/*state*          state
                *encoder*           encoder
                *decoder*           decoder
                *exception-handler* exception-handler]
               (try
                 (apply handler args)
                 (catch Exception e
                   (*exception-handler* e))))))]
        (let [middleware (bound-fn* (partial mw (atom (wl/new-state upgrade-request))))]
          {:on-connect (middleware on-connect)
           :on-error   (middleware on-error)
           :on-close   (middleware on-close)
           :on-text    (middleware on-text)
           :on-bytes   (middleware on-bytes)})))))

