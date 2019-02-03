(ns websocket-layer.encodings-test
  (:require [clojure.test :refer :all])
  (:require [websocket-layer.encodings :refer :all])
  (:import (java.io ByteArrayInputStream)))

(defn input-stream [encoded]
  (cond
    (string? encoded)
    (input-stream (.getBytes encoded))

    (bytes? encoded)
    (ByteArrayInputStream. encoded)))

(deftest test-encoders
  (doseq [{:keys [encoder decoder]} (vals encodings)]
    (let [input   {:test true}
          encoded (encoder input)
          decoded (decoder (input-stream encoded))]
      (is (= decoded input)))))