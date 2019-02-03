(ns websocket-layer.core-test
  (:require [clojure.test :refer :all]
            [websocket-layer.core :as wl]
            [websocket-layer.helpers :as h])
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