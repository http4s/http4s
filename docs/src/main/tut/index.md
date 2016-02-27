---
layout: default
title: http4s
---

```tut:invisible
import org.http4s._
import org.http4s.dsl._
import org.http4s.server._
import org.http4s.server.websocket._
import org.http4s.websocket.WebsocketBits._
import org.http4s.server.blaze._

import scalaz.concurrent._
import scala.concurrent.duration._
import scalaz.stream._
import scalaz.stream.async.unboundedQueue
import scalaz.stream.time.awakeEvery
```

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

```tut:silent
val service = HttpService { case req => Ok("Foo") }
val wcompression = middleware.GZip(service)
val translated   = middleware.URITranslation.translateRoot("/http4s")(service)
```

### Asynchronous

Any http4s response can be streamed from an asynchronous source. http4s offers a variety
of helpers to help you get your data out the door in the fastest way possible without
tying up too many threads.

```tut:silent
// Make your model safe and streaming by using a scalaz-stream Process
def getData(req: Request): Process[Task, String] = Process("one", "two", "three")

val service = HttpService.apply {
  // Wire your data into your service
  case req@GET -> Root / "streaming" => Ok(getData(req))
  // You can use helpers to send any type of data with an available EntityEncoder[T]
  case GET -> Root / "synchronous" => Ok("This is good to go right now.")
}
```

http4s is a forward-looking technology.  HTTP/2.0 and WebSockets will play a central role.

```tut:silent
val route = HttpService {
  case GET -> Root / "hello" =>
    Ok("Hello world.")

  case req@ GET -> Root / "ws" =>
    val src = awakeEvery(1.seconds)(Strategy.DefaultStrategy, DefaultScheduler).map{ d => Text(s"Ping! $d") }
    val sink: Sink[Task, WebSocketFrame] = Process.constant {
      case Text(t, _) => Task.delay( println(t))
      case f       => Task.delay(println(s"Unknown type: $f"))
    }
    WS(Exchange(src, sink))

  case req@ GET -> Root / "wsecho" =>
    val q = unboundedQueue[WebSocketFrame]
    val src = q.dequeue.collect {
      case Text(msg, _) => Text("You sent the server: " + msg)
    }

    WS(Exchange(src, q.enqueue))
}

```scala
BlazeBuilder.bindHttp(8080)
  .withWebSockets(true)
  .mountService(route, "/http4s")
  .run
  .awaitShutdown()
```

## Choose your backend

http4s supports running the same service on multiple backends.  Pick the deployment model that fits your 
needs now, and easily port if and when your needs change.
### blaze

[blaze](http://github.com/http4s/blaze) is an NIO framework.  Run http4s on blaze for maximum throughput.

```scala
object BlazeExample extends App {
  BlazeBuilder.bindHttp(8080)
    .mountService(ExampleService.service, "/http4s")
    .run
    .awaitShutdown()
}
```

### Servlets

http4s is committed to first-class support of the Servlet API.  Develop and deploy services 
on your existing infrastructure, and take full advantage of the mature JVM ecosystem.
http4s can run in a .war on any Servlet 3.0+ container, and comes with convenient builders
for embedded Tomcat and Jetty containers.

```scala
object JettyExample extends App {
  val metrics = new MetricRegistry

  JettyBuilder
    .bindHttp(8080)
    .withMetricRegistry(metrics)
    .mountService(ExampleService.service, "/http4s")
    .mountServlet(new MetricsServlet(metrics), "/metrics/*")
    .run
    .awaitShutdown()
}
```

## An Asynchronous Client ##

http4s also offers an asynchronous HTTP client built on the same model as the server.

```tut:silent
import org.http4s.Http4s._
import org.http4s.client.blaze._
import scalaz.concurrent.Task

val client = PooledHttp1Client()

val page: Task[String] = client.getAs[String](uri("https://www.google.com/"))

for (_ <- 1 to 2)
  println(page.run.take(72))   // each execution of the Task will refetch the page!

// We can do much more: how about decoding some JSON to a scala object
// after matching based on the response status code?
import org.http4s.Status.NotFound
import org.http4s.Status.ResponseClass.Successful
import _root_.argonaut.DecodeJson
import org.http4s.argonaut.jsonOf

case class Foo(bar: String)

implicit val fooDecode = DecodeJson(c => for { // Argonaut decoder. Could also use json4s.
  bar <- (c --\ "bar").as[String]
} yield Foo(bar))

// jsonOf is defined for Json4s and Argonaut, just need the right decoder!
implicit val fooDecoder = jsonOf[Foo]

// Match on response code!
val page2 = client.get(uri("http://http4s.org/resources/foo.json")) {
  case Successful(resp) => resp.as[Foo].map("Received response: " + _)
  case NotFound(resp)   => Task.now("Not Found!!!")
  case resp             => Task.now("Failed: " + resp.status)
}

println(page2.run)

client.shutdown.run
```

## Other features ##

* [twirl](https://github.com/playframework/twirl) integration: use Play framework templates with http4s

## Projects using http4s ##

If you have a project you would like to include in this list, let us know on IRC or submit an issue.

* [httpize](http://httpize.herokuapp.com/): a [httpbin](http://httpbin.org/) built with http4s
* [Project ρ](https://github.com/http4s/rho): a self-documenting HTTP server DSL built upon http4s
* [CouchDB-Scala](https://github.com/beloglazov/couchdb-scala): a purely functional Scala client for CouchDB

## Get it! ##

http4s is built with Java 8. Artifacts for scala 2.10 and 2.11 are available from Maven Central:

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
