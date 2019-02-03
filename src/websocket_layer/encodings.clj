(ns websocket-layer.encodings
  (:require [cheshire.core :as chesh]
            [clojure.java.io :as io]
            [cognitect.transit :as transit]
            [clojure.edn :as edn])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream InputStream)))


(def encodings
  {:edn
   {:encoder
    (fn [data]
      (pr-str data))
    :decoder
    (fn [^InputStream data]
      (with-open [reader (io/reader data)]
        (edn/read reader)))}
   :binary
   {:encoder
    (fn [data] data)
    :decoder
    (fn [^InputStream data]
      (let [buffer (ByteArrayOutputStream.)]
        (io/copy data buffer)
        (.toByteArray buffer)))}
   :json
   {:encoder
    (fn [data]
      (chesh/generate-string data))
    :decoder
    (fn [^InputStream data]
      (with-open [reader (io/reader data)]
        (chesh/parse-stream reader true)))}
   :transit
   {:encoder
    (fn [data]
      (let [output (ByteArrayOutputStream. 2048)
            writer (transit/writer output :json)]
        (transit/write writer data)
        output))
    :decoder
    (fn [^InputStream data]
      (let [stream (ByteArrayInputStream. data)
            reader (transit/reader stream :json)]
        (transit/read reader)))}
   :transit-verbose
   {:encoder
    (fn [data]
      (let [output (ByteArrayOutputStream. 2048)
            writer (transit/writer output :json-verbose)]
        (transit/write writer data)
        output))
    :decoder
    (fn [^InputStream data]
      (let [stream (ByteArrayInputStream. data)
            reader (transit/reader stream :json-verbose)]
        (transit/read reader)))}})