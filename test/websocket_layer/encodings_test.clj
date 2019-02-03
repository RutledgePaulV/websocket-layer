(ns websocket-layer.encodings-test
  (:require [clojure.test :refer :all])
  (:require [websocket-layer.encodings :refer :all])
  (:import (java.io ByteArrayInputStream)))


(deftest test-encoders
  (doseq [{:keys [encoder decoder]} (vals encodings)]
    (let [input   {:test true}
          encoded (encoder input)]
      (is (string? encoded))
      (let [decoded (decoder (ByteArrayInputStream. (.getBytes encoded)))]
        (is (= decoded input))))))