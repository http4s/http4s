# http4s — Common HTTP framework for Scala #

## Type safe HTTP server with native and Servlet backends ##

```scala
// A simple route definition using the optional http4s DSL
val route: HttpService = {
  case Get -> Root / "hello" => Ok("Hello, better world.")
}

// Embedded Jetty Servlet container startup
object ServletExample extends App {
  val server = new Server(8080)
  val context = new ServletContextHandler()
  context.setContextPath("/")
  server.setHandler(context);
  context.addServlet(new ServletHolder(route), "/http4s/*")
  server.start()
  server.join()
}

// Native blaze server startup
object BlazeWebSocketExample extends App {
  def pipebuilder(): LeafBuilder[ByteBuffer] =
    new Http1Stage(URITranslation.translateRoot("/http4s")(route)) with WebSocketSupport

  new SocketServerChannelFactory(pipebuilder, 12, 8*1024)
        .bind(new InetSocketAddress(8080))
        .run()
}

```

## Simple and type safe streaming of HTTP and websockets with scalaz-stream Processes ##

```scala
def getData(req: Request): Process[Task, String] = ???

val route: HttpService = {
  case Get -> Root / "streaming" =>
    Ok(getData(req))

  case req@ Get -> Root / "ws" =>
    val src = Process.awakeEvery(1.seconds).map{ d => Text(s"Ping! $d") }
    val sink: Sink[Task, WSFrame] = Process.constant {
      case Text(t) => Task.delay( println(t))
      case f       => Task.delay(println(s"Unknown type: $f"))
    }.onComplete(Process.eval(Task{ println("Terminated!")}).drain)
    WS(src, sink)

  case req@ Get -> Root / "wsecho" =>
    val t = topic[WSFrame]  // topics act as a hub to publish and subscribe to messages safely
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
