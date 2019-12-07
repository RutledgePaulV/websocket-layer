(ns websocket-layer.core-test
  (:require [clojure.test :refer :all]
            [websocket-layer.core :as wl]
            [websocket-layer.helpers :as h]
            [clojure.core.async :as async])
  (:import (java.util UUID)))

(defonce fixtures
  (use-fixtures :each h/fixture))

(deftest testing-client-push
  (let [received (atom [])]
    (defmethod wl/handle-push :message [data]
      (swap! received conj data))
    (h/send-> h/*client*
              {:id    (UUID/randomUUID)
               :proto :push
               :data  {:kind :message}})
    (Thread/sleep 100)
    (is (= [{:kind :message}] @received))))

(deftest testing-request-response
  (let [request  {:id    (UUID/randomUUID)
                  :proto :request
                  :data  {:kind :message}}
        received (atom [])]
    (defmethod wl/handle-request :message [data]
      (swap! received conj data)
      {:success true})
    (h/send-> h/*client* request)
    (let [response (h/<-receive h/*client*)]
      (is (= [{:kind :message}] @received))
      (is (= (assoc request :data {:success true}) response)))))

(deftest testing-subscriptions-client-close
  (let [request   {:id    (UUID/randomUUID)
                   :proto :subscription
                   :data  {:kind :message}}
        responses (async/chan 100)]
    (defmethod wl/handle-subscription :message [data]
      (async/>!! responses {:result 1})
      (async/>!! responses {:result 2})
      responses)
    (h/send-> h/*client* request)
    (is (= {:result 1} (:data (h/<-receive h/*client*))))
    (is (= {:result 2} (:data (h/<-receive h/*client*))))
    (is (not (h/closed? responses)))
    (h/send-> h/*client* (assoc request :close true))
    (Thread/sleep 100)
    (is (h/closed? responses))))

(deftest testing-subscriptions-server-close
  (let [request   {:id    (UUID/randomUUID)
                   :proto :subscription
                   :data  {:kind :message}}
        responses (async/chan 100)]
    (defmethod wl/handle-subscription :message [data]
      (async/>!! responses {:result 1})
      (async/>!! responses {:result 2})
      (future (Thread/sleep 500) (async/close! responses))
      responses)
    (h/send-> h/*client* request)
    (is (= {:result 1} (:data (h/<-receive h/*client*))))
    (is (= {:result 2} (:data (h/<-receive h/*client*))))
    (is (not (h/closed? responses)))
    (Thread/sleep 600)
    (is (h/closed? responses))
    (is (:close (h/<-receive h/*client*)))))