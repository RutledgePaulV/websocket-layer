(ns websocket-layer.encodings
  (:require [jsonista.core :as json]
            [clojure.java.io :as io]
            [cognitect.transit :as transit]
            [clojure.edn :as edn])
  (:import (java.io ByteArrayOutputStream InputStream PushbackReader)))


(def mapper
  (json/object-mapper
    {:encode-key-fn (fn [x] (if (keyword? x) (name x) x))
     :decode-key-fn (fn [x] (if (string? x) (keyword x) x))}))

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
      (json/write-value-as-string data mapper))
    :decoder
    (fn [^InputStream data]
      (with-open [reader (io/reader data)]
        (json/read-value reader mapper)))}
   :transit-json
   {:encoder
    (fn [data]
      (let [output (ByteArrayOutputStream. 2048)
            writer (transit/writer output :json)]
        (transit/write writer data)
        (String. (.toByteArray output))))
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
        (String. (.toByteArray output))))
    :decoder
    (fn [^InputStream data]
      (let [reader (transit/reader data :json-verbose)]
        (transit/read reader)))}})