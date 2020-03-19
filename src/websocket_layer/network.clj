(ns websocket-layer.network
  (:require [clojure.core.async :as async]
            [ring.adapter.jetty9.websocket :as jws]
            [websocket-layer.core :as wl]
            [websocket-layer.encodings :as enc]
            [clojure.string :as strings])
  (:import (java.io ByteArrayInputStream)
           (org.eclipse.jetty.io EofException)
           (org.eclipse.jetty.websocket.api CloseException WriteCallback RemoteEndpoint)
           (io.aleph.dirigiste Executors)
           (java.util.concurrent Executor)
           (java.nio.channels ClosedChannelException)))

(def ^:dynamic *encoder*)
(def ^:dynamic *decoder*)
(def ^:dynamic *exception-handler*)
(def ^:dynamic ^Executor *executor*)

(defmacro quietly
  "Execute the body and return nil if there was an error"
  [& body]
  `(try ~@body (catch Throwable _# nil)))

(defn insignificant? [e]
  (or (nil? e)
      (instance? EofException e)
      (instance? CloseException e)
      (instance? ClosedChannelException e)))

(defn handle-exception [e]
  (when-not (insignificant? e)
    (*exception-handler* e)))

(defmacro safe-future [& body]
  `(let [fun#
         (bound-fn*
           (^{:once true} fn* []
             (try ~@body
                  (catch Exception e#
                    (handle-exception e#)))))]
     (.execute *executor* fun#)))

(defn send-message! [ws data]
  (let [finished (async/promise-chan)
        callback (#'jws/write-callback
                   {:write-failed
                    (fn [e]
                      (try
                        (handle-exception e)
                        (finally
                          (async/close! finished))))
                    :write-success
                    (fn [] (async/close! finished))})]
    (try
      (jws/send! ws
        (fn [^RemoteEndpoint endpoint]
          (when (some? endpoint)
            (let [msg (*encoder* data)]
              (.sendString endpoint msg ^WriteCallback callback)))))
      (catch Exception e
        (try
          (handle-exception e)
          (finally
            (async/close! finished)))))
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
  (handle-exception e))

(defn on-close [_ _ _]
  ; remove visibility of any ongoing activities
  (let [[{:keys [id outbound subscriptions]}] (reset-vals! wl/*state* {})]

    (swap! wl/sockets dissoc id)

    (quietly (async/close! outbound))

    (doseq [sub (vals subscriptions)]
      ; close all the open subscriptions
      (quietly (async/close! sub)))))

(defn on-command [_ {topic :id
                     proto :proto
                     data  :data
                     close :close
                     :or   {data {} close false proto :push}}]

  (let [closure wl/*state*
        {:keys [outbound subscriptions]} (deref closure)]

    (case (some-> proto name strings/lower-case keyword)

      :request
      (safe-future
        (let [response (wl/handle-request data)]
          (async/put! outbound {:data response :proto proto :id topic})))

      :subscription
      (cond

        (true? close)
        (when-some [sub (get subscriptions topic)]
          (async/close! sub))

        (contains? subscriptions topic)
        nil

        :otherwise
        (safe-future
          (when-some [response (wl/handle-subscription data)]
            (swap! closure assoc-in [:subscriptions topic] response)
            (async/go-loop []
              (if-some [res (async/<! response)]
                (if (async/>! outbound {:data res :proto proto :id topic})
                  (recur)
                  (do (swap! closure update :subscriptions dissoc topic)
                      (async/close! response)))
                (let [[{old-subs :subscriptions}]
                      (swap-vals! closure update :subscriptions dissoc topic)]
                  (when (contains? old-subs topic)
                    (async/>! outbound {:proto proto :id topic :close true}))))))))

      :push
      (safe-future (wl/handle-push data)))))

(defn on-text [ws message]
  (on-command ws (with-open [stream (ByteArrayInputStream. (.getBytes message))]
                   (*decoder* stream))))

(defn on-bytes [ws bites offset len]
  (let [buffer (byte-array len)
        _      (System/arraycopy bites offset buffer 0 len)]
    (on-command ws (with-open [stream (ByteArrayInputStream. buffer)]
                     (*decoder* stream)))))

(defn websocket-handler
  [{:keys [exception-handler encoding encoder decoder middleware target-utilization max-threads]
    :or   {encoding           :edn
           middleware         []
           target-utilization 0.8
           max-threads        1000
           exception-handler  (fn [^Exception exception]
                                (if-some [handler (Thread/getDefaultUncaughtExceptionHandler)]
                                  (.uncaughtException handler (Thread/currentThread) exception)
                                  (.printStackTrace exception)))}}]
  (let [encoder  (or encoder (get-in enc/encodings [encoding :encoder]))
        decoder  (or decoder (get-in enc/encodings [encoding :decoder]))
        executor (Executors/utilizationExecutor target-utilization max-threads)]
    (->> (Thread. ^Runnable (fn [] (.shutdown executor)))
         (.addShutdownHook (Runtime/getRuntime)))
    (letfn [(message-bindings [handler state]
              (fn [& args]
                (binding
                  [wl/*state*          state
                   *encoder*           encoder
                   *decoder*           decoder
                   *executor*          executor
                   *exception-handler* exception-handler]
                  (apply handler args))))
            (exception-handling [handler]
              (fn [& args]
                (try
                  (apply handler args)
                  (catch Exception e
                    (handle-exception e)))))
            (custom-middlewares [handler]
              (reduce #(%2 %1) handler middleware))
            (mw [state handler]
              (-> handler
                  (custom-middlewares)
                  (exception-handling)
                  (message-bindings state)
                  (bound-fn*)))]
      (fn [upgrade-request]
        (let [state (atom (wl/new-state upgrade-request))]
          {:on-connect (mw state on-connect)
           :on-error   (mw state on-error)
           :on-close   (mw state on-close)
           :on-text    (mw state on-text)
           :on-bytes   (mw state on-bytes)})))))

