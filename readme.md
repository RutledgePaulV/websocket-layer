[![Build Status](https://travis-ci.com/RutledgePaulV/websocket-layer.svg?branch=develop)](https://travis-ci.com/RutledgePaulV/websocket-layer)
[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.rutledgepaulv/websocket-layer.svg)](https://clojars.org/org.clojars.rutledgepaulv/websocket-layer)

## Websocket Layer

A clojure library that provides a little glue between jetty websockets and core.async.
This library only provides the server implementation. I also created a re-frame client-side
counterpart called [websocket-fx](https://github.com/RutledgePaulV/websocket-fx).

___

### Why?

Websockets are still low on the abstraction level. This library provides a few
patterns and state management on top of raw websockets that are closer to what 
I want within my applications.


### Installation



```clojure
(require '[websocket-layer.network :as net])
(require '[ring.adapter.jetty9 :as jetty])

(defn web-handler [request]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "<html><head></head><body></body></html>"})

(def ws-endpoints 
  {"/ws" (net/websocket-handler {:encoding :json})})

; other available encodings include: :edn, :transit-json, and :transit-json-verbose

(def ring-options 
  {:port                 3000 
   :join?                false
   :async?               true 
   :websockets           ws-endpoints
   :allow-null-path-info true})
  
(def server (jetty/run-jetty web-handler ring-options))
```


___


### Message Patterns:

#### Topic Subscriptions

Subscribe to a topic and get potentially multiple messages related to the topic. 
If the client or server are no longer interested in a topic, they can send a hint
to the other side to cleanup any associated resources. This library handles cleaning
things up on the server when the client loses interest.

Initial request messages look like this:

```clojure
{:id    "some-subscription-id"
 :proto :subscription
 :data  {:kind :dispatch-key
         :stuff :you-send}}
```

Response messages look like this:

```clojure
{:id    "some-subscription-id"
 :proto :subscription
 :data  {:stuff :you-return}}
```

Either side can notify the other that it has lost interest in the topic:

```clojure
{:id    "some-subscription-id"
 :proto :subscription
 :close true}
```

Handlers look like this:

```clojure
(defmethod wl/handle-subscription :dispatch-key [{:keys [kind stuff]}]
 (let [results (async/chan)]
   (async/go-loop []
     (async/<! (async/timeout 5000))
     (async/>! results {:stuff :you-return})
     (recur))
   results))
```

___


#### Request / Response

Send a request over a websocket and return a reply. You can use this
as an xhr alternative. Requests look like this:

```clojure
{:id    "some-request-id"
 :proto :request
 :data  {:kind  :dispatch-key
         :stuff :you-send}}
```

Responses look like this:

```clojure
{:id    "some-request-id"
 :proto :request
 :data  {:stuff :you-return}}
```

Handlers look like this:

```clojure
(defmethod wl/handle-request :dispatch-key [{:keys [kind stuff]}]
 {:stuff :you-return})
```

___

#### Client Pushes

Messages sent to the server look like this:

```clojure
{:id    "some-push-id"
 :proto :push
 :data  {:kind  :dispatch-key
         :stuff :you-send}}
```

There is no server->client equivalent. Server push doesn't really make any sense. 
In order for a message sent by the server to be useful to the client, the client 
has to be prepared to handle messages of that type. Instead, clients should indicate 
their desire for messages from the server by initiating a subscription.


Handlers look like this:

```clojure
(defmethod wl/handle-push :dispatch-key [{:keys [kind stuff]}]
 (do-something-with-stuff stuff)
 :return-value-is-ignored)
```

___


### Other Library Features


#### Websocket Local State

```clojure
(defmethod wl/handle-push :record-nav [data]
 (wl/swap-state! update :history (fnil conj []) (get data :page)))
 
(defmethod wl/handle-request :navigation-history [data]
 {:history (get (wl/get-state) :history [])})
```

#### Connection Tracking

```clojure
(defmethod wl/handle-request :number-of-peers [data]
 {:peers (count @wl/sockets)})
```


___ 

### FAQ

#### How does it compare to Sente?

Maybe it's just me, but I don't like sente. I wanted something 
lightweight that works with jetty adapters and otherwise stays 
out of my way. I don't need long-polling fallback.

#### What about broadcast?

I don't intend to provide anything for this out of box since it's
easy to implement yourself using subscriptions and atoms to keep
track of the connections that belong in a particular broadcast group.

#### Recommended clients?

* [Haslett](https://github.com/weavejester/haslett)
