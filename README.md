# http4s — Common HTTP framework for Scala #

## Type safe HTTP server using Servlet or Native backends ##

http4s is intended to provide the foundation for lightweight frameworks or as a standalone server.
Future versions will also include a HTTP client, and a basic one can be found in the blaze project.

### Make a HttpService ###

If you want to use http4s standalone, all you need to do is define a HttpService, and fire up a backend.
A HttpService is just a PartialFunction[Request,Task[Response]]. http4s provides a variety
of helpers to facilitate the creation of the Task[Response] from common results.

```scala
// A simple route definition using the optional http4s DSL
val route: HttpService = {
  //  We use the micro DSL to match the path of the Request to the familiar uri form
  case Get -> Root / "hello" =>
    // We could make a Task[Response] manually, but we use the
    // EntityResponseGenerator 'Ok' for convenience
    Ok("Hello, better world.")
```

Not only does making a HttpService a PartialFunction make it simple to define a service,
it also makes it easy to compose them. Adding Gzip compression or translating the path is
as simple as applying a middleware to a HttpService.

```scala
val wcompression = middleware.GZip(route)
val translated   = middleware.URITranslation.translateRoot("/http4s")(route)
```

### Run your HttpService using Jetty ###
```scala
object ServletExample extends App {
  val server = new Server(8080)
  val context = new ServletContextHandler()
  context.setContextPath("/")
  server.setHandler(context)
  context.addServlet(new ServletHolder(route), "/http4s/*")
  server.start()
  server.join()
}
```

### Run your HttpService using the fast native backend, blaze ###
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

## Simple and type safe streaming with scalaz-stream Processes ##

In http4s any response can be type safe, streamed, and completely asynchronous. http4s offers a variety
of helpers to help you get your data out the door in the fastest way possible.

```scala
// Make your model safe and streaming be using a scalaz-stream Process
def getData(req: Request): Process[Task, String] = ???

val route: HttpService = {
  // Wire your data into your service
  case Get -> Root / "streaming" => Ok(getData(req))

  // You can use helpers to send any type of data with an available Writable[T]
  case Get -> Root / "synchronous" => Ok("This is good to go right now.")
}
```

### WebSockets using the blaze backend ###

http4s is a forward looking technology and HTTP/2.0 and WebSockets will play a central role.

```scala
val route: HttpService = {
  case req@ Get -> Root / "ws" =>
    // Send a Text message with payload 'Ping! delay' every second
    val src = Process.awakeEvery(1.seconds).map{ d => Text(s"Ping! $d") }

    // Print received Text frames, and, on completion, notify the console
    val sink: Sink[Task, WSFrame] = Process.constant {
      case Text(t) => Task.delay( println(t))
      case f       => Task.delay(println(s"Unknown type: $f"))
    }.onComplete(Process.eval(Task{ println("Terminated!")}).drain)

    // Use the WS helper to make the Task[Response] carrying the info
    // needed for the backend to upgrade to a WebSocket connection
    WS(src, sink)

  case req@ Get -> Root / "wsecho" =>
    // a scalaz topic acts as a hub to publish and subscribe to messages safely
    val t = topic[WSFrame]
    val src = t.subscribe.collect{ case Text(msg) => Text("You sent the server: " + msg) }
    WS(src, t.publish)
}
```

## Build & run ##

```sh
$ cd http4s
$ sbt examples/run
```

## Contact ##

- #http4s on Freenode

## Continuous Integration ##

[![Build Status](https://travis-ci.org/http4s/http4s.svg?branch=develop)](https://travis-ci.org/http4s/http4s)
