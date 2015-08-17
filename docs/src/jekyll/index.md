---
layout: default
title: http4s
---

http4s is a minimal, idiomatic Scala interface for HTTP.  http4s is Scala's answer to Ruby's 
Rack, Python's WSGI, Haskell's WAI, and Java's Servlets.

## HttpService ##

An `HttpService` transforms a `Request` into an asynchronous `Task[Response]`. http4s provides a variety
of helpers to facilitate the creation of the `Task[Response]` from common results.

HttpServices are _type safe_, _composable_, and _asynchronous_.

### Type safety

`Request` and `Response` sit at the top level of a typed, immutable model of HTTP.

* Well-known headers are lazily parsed into a rich model derived from Spray HTTP.
* Bodies are parsed and generated from a [scalaz-stream](http://github.com/scalaz/scalaz-stream) of bytes.

### Composable

Building on the FP tools of scalaz not only makes an `HttpService` simple to define,
it also makes them easy to compose.  Adding gzip compression or rewriting URIs is
as simple as applying a middleware to an `HttpService`.

```scala
val wcompression = middleware.GZip(service)
val translated   = middleware.URITranslation.translateRoot("/http4s")(service)
```

### Asynchronous

Any http4s response can be streamed from an asynchronous source. http4s offers a variety
of helpers to help you get your data out the door in the fastest way possible without
tying up too many threads.

```scala
// Make your model safe and streaming by using a scalaz-stream Process
def getData(req: Request): Process[Task, String] = ???

val service = HttpService {
  // Wire your data into your service
  case GET -> Root / "streaming" => Ok(getData(req))

  // You can use helpers to send any type of data with an available EntityEncoder[T]
  case GET -> Root / "synchronous" => Ok("This is good to go right now.")
}
```

http4s is a forward-looking technology.  HTTP/2.0 and WebSockets will play a central role.

{%code_ref ../../../examples/blaze/src/main/scala/com/example/http4s/blaze/BlazeWebSocketExample.scala blaze_websocket_example %}

## Choose your backend

http4s supports running the same service on multiple backends.  Pick the deployment model that fits your 
needs now, and easily port if and when your needs change.
### blaze

[blaze](http://github.com/http4s/blaze) is an NIO framework.  Run http4s on blaze for maximum throughput.

{%code_ref ../../../examples/blaze/src/main/scala/com/example/http4s/blaze/BlazeExample.scala blaze_server_example %}

### Servlets

http4s is committed to first-class support of the Servlet API.  Develop and deploy services 
on your existing infrastructure, and take full advantage of the mature JVM ecosystem.
http4s can run in a .war on any Servlet 3.0+ container, and comes with convenient builders
for embedded Tomcat and Jetty containers.

{%code_ref ../../../examples/jetty/src/main/scala/com/example/http4s/jetty/JettyExample.scala jetty_example %}

## An Asynchronous Client ##

http4s also offers an asynchronous HTTP client built on the same model as the server.

{%code_ref ../../../examples/blaze/src/main/scala/com/example/http4s/blaze/ClientExample.scala blaze_client_example %}

## Other features ##

* [twirl](https://github.com/playframework/twirl) integration: use Play framework templates with http4s


## Projects using http4s ##

If you have a project you would like to include in this list, let us know on IRC or submit an issue.

* [httpize](http://httpize.herokuapp.com/): a [httpbin](http://httpbin.org/) built with http4s
* [Project ρ](https://github.com/http4s/rho): a self-documenting HTTP server DSL built upon http4s
* [CouchDB-Scala](https://github.com/beloglazov/couchdb-scala): a purely functional Scala client for CouchDB

## Get it! ##

Artifacts for scala 2.10 and 2.11 are available from Maven Central:

```scala
libraryDependencies += "org.http4s" %% "http4s-dsl"          % version  // to use the core dsl
libraryDependencies += "org.http4s" %% "http4s-blaze-server" % version  // to use the blaze backend
libraryDependencies += "org.http4s" %% "http4s-servlet"      % version  // to use the raw servlet backend
libraryDependencies += "org.http4s" %% "http4s-jetty"        % version  // to use the jetty servlet backend
libraryDependencies += "org.http4s" %% "http4s-blaze-client" % version  // to use the blaze client
```

Snapshots for the development branch are available in the sonatype snapshots repos.

To get scalaz-stream artifacts, you will probably need to add BinTray to your resolvers:

```scala
resolvers += "Scalaz Bintray Repo" at "http://dl.bintray.com/scalaz/releases"
```

## Build & run ##

```sh
$ git clone https://github.com/http4s/http4s.git
$ cd http4s
$ sbt examples-blaze/run
```
