(ns websocket-layer.encodings
  (:require [jsonista.core :as json]
            [clojure.java.io :as io]
            [cognitect.transit :as transit]
            [clojure.edn :as edn])
  (:import (java.io ByteArrayOutputStream InputStream PushbackReader)
           (clojure.lang Reflector)))

(defn class-exists? [s]
  (try (boolean (Class/forName s))
       (catch ClassNotFoundException e
         false)))

(defn instantiate [s & args]
  (Reflector/invokeConstructor
    (resolve (symbol s)) (to-array args)))

(defn get-modules []
  (->> ["com.fasterxml.jackson.datatype.joda.JodaModule"
        "com.fasterxml.jackson.datatype.jdk8.Jdk8Module"]
       (filter class-exists?)
       (mapv instantiate)))

(def mapper
  (delay
    (json/object-mapper
      {:encode-key-fn true
       :decode-key-fn true
       :modules       (get-modules)})))

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
      (json/write-value-as-string data @mapper))
    :decoder
    (fn [^InputStream data]
      (with-open [reader (io/reader data)]
        (json/read-value reader @mapper)))}
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