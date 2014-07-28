---
layout: default
title: http4s
---

# http4s

http4s is a minimal, idiomatic Scala interface for HTTP services.  http4s is Scala's answer to Ruby's Rack, Python's WSGI, Haskell's WAI, and Java's Servlets.

## HttpService ##

An `HttpService` is just a `PartialFunction[Request, Task[Response]]`.  http4s provides a variety
of helpers to facilitate the creation of the `Task[Response]` from common results.

{% code_ref ../../examples/src/main/scala/org/http4s/examples/site/HelloBetterWorld.scala service %}

HttpServices are _type safe_, _composable_, and _asynchronous_.

### Type safety

`Request` and `Response` sit at the top level of a typed, immutable model of HTTP.

* Well-known headers are lazily parsed into a rich model derived from Spray HTTP.
* Bodies are parsed and generated from a [scalaz-stream](http://github.com/scalaz/scalaz-stream) of bytes.

### Composable

Not only does making an `HttpService` a `PartialFunction` make it simple to define a service,
it also makes it easy to compose them.  Adding gzip compression or rewriting URIs is
as simple as applying a middleware to an `HttpService`.

```scala
val wcompression = middleware.GZip(service)
val translated   = middleware.URITranslation.translateRoot("/http4s")(service)
```

### Asynchronous

Any http4s response can be streamed from an asynchronous source. http4s offers a variety
of helpers to help you get your data out the door in the fastest way possible.

```scala
// Make your model safe and streaming by using a scalaz-stream Process
def getData(req: Request): Process[Task, String] = ???

val service: HttpService = {
  // Wire your data into your service
  case GET -> Root / "streaming" => Ok(getData(req))

  // You can use helpers to send any type of data with an available Writable[T]
  case GET -> Root / "synchronous" => Ok("This is good to go right now.")
}
```

http4s is a forward-looking technology.  HTTP/2.0 and WebSockets will play a central role.

```scala
val route: HttpService = {
  case req@ GET -> Root / "ws" =>
    // Send a Text message with payload 'Ping!' every second
    val src = Process.awakeEvery(1.seconds).map{ d => Text(s"Ping! $d") }

    // Print received Text frames, and, on completion, notify the console
    val sink: Sink[Task, WSFrame] = Process.constant {
      case Text(t) => Task.delay(println(t))
      case f       => Task.delay(println(s"Unknown type: $f"))
    }.onComplete(Process.eval(Task{ println("Terminated!")}).drain)

    // Use the WS helper to make the Task[Response] carrying the info
    // needed for the backend to upgrade to a WebSocket connection
    WS(src, sink)

  case req @ GET -> Root / "wsecho" =>
    // a scalaz topic acts as a hub to publish and subscribe to messages safely
    val t = topic[WSFrame]
    val src = t.subscribe.collect{ case Text(msg) => Text("You sent the server: " + msg) }
    WS(src, t.publish)
}
```

## Choose your backend

http4s supports running the same service on multiple backends.  Pick the deployment model that fits your needs now, and easily port if and when your needs change.

### blaze

[blaze](http://github.com/http4s/blaze) is an NIO framework.  Run http4s on blaze for maximum throughput.

```scala
object BlazeWebSocketExample extends App {
  // Provides a template for the blaze pipeline
  def pipebuilder(): LeafBuilder[ByteBuffer] =
    new Http1Stage(URITranslation.translateRoot("/http4s")(route)) with WebSocketSupport

  // Bind the socket and begin serving
  new SocketServerChannelFactory(pipebuilder, 12, 8*1024)
    .bind(new InetSocketAddress(8080))
    .run()
}
```

### Servlets

http4s is committed to first-class support of the Servlet API.  Develop and deploy services on your existing infrastructure, and take full advantage of the mature JVM ecosystem.

```scala
object JettyExample extends App {
  JettyServer.newBuilder
    .mountService(ExampleService.service, "/http4s")
    .run()
    .join()
}
```

## Get it! ##

Artifacts for scala 2.10 and 2.11 are available from Maven Central:
```scala
libraryDependencies += "org.http4s" %% "http4s-core"  % version
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
$ sbt examples/run
```

## Project info ##

* [GitHub](http://github.com/http4s/http4s)
* [Travis CI ![BuildStatus](https://travis-ci.org/http4s/http4s.svg?branch=develop)](https://travis-ci.org/http4s/http4s)
* [Scaladoc](http://http4s.org/api/0.2)
* IRC: #http4s on Freenode.
* [Twitter](http://twitter.com/http4s)

## Credits
* [The Contributors](https://github.com/http4s/http4s/graphs/contributors?from=2013-01-01&type=c), as calculated by GitHub.
* HTTP model forked from [spray-http](http://spray.io/documentation/1.2.1/spray-http/), which derives from [Blueeyes](https://github.com/jdegoes/blueeyes).
