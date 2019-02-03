(ns websocket-layer.encodings
  (:require [cheshire.core :as chesh]
            [clojure.java.io :as io]
            [cognitect.transit :as transit]
            [clojure.edn :as edn])
  (:import (java.io ByteArrayOutputStream InputStream PushbackReader)))


(def encodings
  {:edn
   {:encoder
    (fn [data]
      (pr-str data))
    :decoder
    (fn [^InputStream data]
      (with-open [reader (io/reader data)]
        (edn/read (PushbackReader. reader))))}
   :json
   {:encoder
    (fn [data]
      (chesh/generate-string data))
    :decoder
    (fn [^InputStream data]
      (with-open [reader (io/reader data)]
        (chesh/parse-stream reader true)))}
   :transit-json
   {:encoder
    (fn [data]
      (let [output (ByteArrayOutputStream. 2048)
            writer (transit/writer output :json)]
        (transit/write writer data)
        (.toByteArray output)))
    :decoder
    (fn [^InputStream data]
      (let [reader (transit/reader data :json)]
        (transit/read reader)))}
   :transit-json-verbose
   {:encoder
    (fn [data]
      (let [output (ByteArrayOutputStream. 2048)
            writer (transit/writer output :json-verbose)]
        (transit/write writer data)
        (.toByteArray output)))
    :decoder
    (fn [^InputStream data]
      (let [reader (transit/reader data :json-verbose)]
        (transit/read reader)))}})