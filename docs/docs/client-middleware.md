# Client middleware

Client middleware wraps a [Client] to add functionality. This document is a
list of included client middleware, which can be found
in the `org.http4s.client.middleware` package.

First we prepare a server that we can make requests to:
```scala mdoc:silent
import cats.effect._
import cats.syntax.all._
import org.http4s._
import io.circe._
import io.circe.syntax._
import io.circe.jawn._
import org.http4s.headers._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.client.Client
import cats.effect.unsafe.IORuntime
import scala.concurrent.duration._
import cats.effect.std.{Console, Random}

implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
val allRhinoFacts = List(
  "Rhinoceros horns are made from keratin, just like human fingernails.",
  "The rhino is the second largest land animal, after the elephant.",
  "The gestation period for a rhinoceros baby is almost 15 months.",
)

val service = HttpRoutes.of[IO] {
  case GET -> Root / "redirect" => MovedPermanently(Location(uri"/ok"))
  case GET -> Root / "ok" => Ok("👍")
  case r@GET -> Root / "rhinoFacts" =>
    // show a rhino fact, try to not repeat a fact the user already has seen
    val knownFacts: List[Int] = r.cookies.find(_.name == "rhinoFacts")
      .flatMap(cookie => decode[List[Int]](cookie.content).toOption)
      .toList
      .flatten

    // choose the index of a fact
    val factIndex =
      Random.scalaUtilRandom[IO]
        .flatMap(rng => rng.shuffleList(List.range(0, allRhinoFacts.size).filterNot(knownFacts.contains_)))
        .map(_.headOption)

    factIndex
      .flatMap { index =>
        val cookie = index.map(_ :: knownFacts).getOrElse(knownFacts).asJson.noSpaces
        val response = index.flatMap(allRhinoFacts.get(_)).getOrElse("You know all the facts!")
        Ok(response).map(_.addCookie("rhinoFacts", cookie))
      }
}

val client = Client.fromHttpApp(service.orNotFound)
```
```scala mdoc:invisible
// we define our own Console[IO] to sidestep some mdoc issues: https://github.com/scalameta/mdoc/issues/517

import cats.Show

implicit val mdocConsoleIO: Console[IO] = new Console[IO] {
  val mdocConsoleOut = scala.Console.out

  def println[A](a: A)(implicit s: Show[A] = Show.fromToString[A]): IO[Unit] = {
    val str = s.show(a)
    IO.blocking(mdocConsoleOut.println(str))
  }

  def print[A](a: A)(implicit S: Show[A] = Show.fromToString[A]): IO[Unit] = IO.unit

  def error[A](a: A)(implicit S: Show[A] = Show.fromToString[A]): IO[Unit] = IO.unit

  def errorln[A](a: A)(implicit S: Show[A] = Show.fromToString[A]): IO[Unit] = IO.unit

  def readLineWithCharset(charset: java.nio.charset.Charset): IO[String] = IO.pure("")
}
```

## CookieJar
Enhances a client to store and supply cookies. An in-memory implementation is provided,
but it's also possible to supply your own method of persistence, see [CookieJar].
In this example the service will use the `rhinoFacts` cookie to track the facts that have been shown to the client.

```scala mdoc:silent
import org.http4s.client.middleware.CookieJar

// the domain is necessary because cookies are tied to a domain
val factRequest = Request[IO](Method.GET, uri"http://example.com/rhinoFacts")
```
```scala mdoc
// without cookies the server will repeat facts
client.expect[String](factRequest)
  .flatMap(Console[IO].println)
  .replicateA(4)
  .void
  .unsafeRunSync()

// the server won't repeat facts because the client indicates what it already knows
CookieJar.impl(client).flatMap { cl =>
  cl.expect[String](factRequest).flatMap(Console[IO].println).replicateA(4).void
}.unsafeRunSync()
```

## DestinationAttribute

This very simple middleware writes a value to the request attributes, which can be
read at any point during the processing of the request, by other middleware down the line.

In this example we create our own middleware that appends a header to the response. We use
`DestinationAttribute` to provide the value.

```scala mdoc:silent
import org.http4s.client.middleware.DestinationAttribute
import org.http4s.client.middleware.DestinationAttribute.Destination

def myMiddleware(cl: Client[IO]): Client[IO] = Client { req =>
  val destination = req.attributes.lookup(Destination).getOrElse("")
  cl.run(req).map(_.putHeaders("X-Destination" -> destination))
}

val mwClient = DestinationAttribute(myMiddleware(client), "example")
```

```scala mdoc
mwClient.run(Request[IO](Method.GET, uri"/ok")).use(_.headers.pure[IO]).unsafeRunSync()
```

## FollowRedirects

Allows a client to interpret redirect responses and follow them. See [FollowRedirect]
for configuration.

```scala mdoc:silent
import org.http4s.client.middleware.FollowRedirect

val redirectRequest = Request[IO](Method.GET, uri"/redirect")
```

```scala mdoc
client.status(redirectRequest).unsafeRunSync()
FollowRedirect(maxRedirects = 3)(client).status(redirectRequest).unsafeRunSync()
```

## Logger, ResponseLogger, RequestLogger

Log requests and responses. `ResponseLogger` logs the responses, `RequestLogger`
logs the request, `Logger` logs both.

```scala mdoc:silent
import org.http4s.client.middleware.Logger

val loggerClient = Logger[IO](
  logHeaders = false,
  logBody = true,
  logAction = Some((msg: String) => Console[IO].println(msg))
)(client)

```

```scala mdoc
loggerClient.expect[Unit](Request[IO](Method.GET, uri"/ok")).unsafeRunSync()
```

## GZip

Adds support for gzip compression. The client will indicate it can read gzip responses
and if the server responds with gzip, the client will decode the response transparently.

```scala mdoc:silent
import org.http4s.client.middleware.GZip
import org.http4s.server.middleware.{GZip => ServerGZip}

val gzipService = ServerGZip(
  HttpRoutes.of[IO] { case GET -> Root / "long" => Ok("0123456789" * 5) }
).orNotFound
val clientWithoutGzip = Client.fromHttpApp(gzipService)
val clientWithGzip = GZip()(clientWithoutGzip)
val longRequest = Request[IO](Method.GET, uri"/long")
```

```scala mdoc
// without gzip in our client, we get the full body (shown in hex) 
clientWithoutGzip
  .run(longRequest)
  .use(_.body.through(fs2.text.hex.encode).compile.foldMonoid)
  .unsafeRunSync()

// if we specify that the client understands gzip we get a smaller response 
clientWithoutGzip
  .run(longRequest.putHeaders("Accept-Encoding" -> "gzip"))
  .use(_.body.through(fs2.text.hex.encode).compile.foldMonoid)
  .unsafeRunSync()

// with the middleware the response is decompressed transparently 
clientWithGzip
  .run(longRequest)
  .use(_.body.through(fs2.text.hex.encode).compile.foldMonoid)
  .unsafeRunSync()
```

## Retry

Allows a client to handle server errors by retrying requests. See [Retry] and
[RetryPolicy] for ways of configuring the retry policy.

```scala mdoc:silent
import org.http4s.client.middleware.{Retry, RetryPolicy}

object Bail extends Exception("not yet")

// a service that fails the first three times it's called
val flakyService =
  Ref[IO].of(0).map { attempts =>
    HttpRoutes.of[IO] {
      case _ => attempts.getAndUpdate(_ + 1)
        .flatMap(a => IO.raiseWhen(a < 3)(Bail) >> Ok("ok"))
    }
  }

val policy = RetryPolicy[IO](backoff = _ => Some(1.milli))

```

```scala mdoc
// without the middleware the call will fail
flakyService.flatMap { service =>
  val client = Client.fromHttpApp(service.orNotFound)
  client.expect[String](Request[IO](uri = uri"/")).attempt
}.unsafeRunSync()

// with the middleware the call will succeed eventually
flakyService.flatMap { service =>
  val client = Client.fromHttpApp(service.orNotFound)
  val retryClient = Retry(policy)(client)
  retryClient.expect[String](Request[IO](uri = uri"/"))
}.unsafeRunSync()
```

## UnixSocket

[Unix domain sockets] are an operating system feature which allows communication between processes
while not needing to use the network.

This middleware allows a client to make requests to a domain socket.

```scala mdoc:silent
import fs2.io.file._
import fs2.io.net.unixsocket.UnixSocketAddress
import fs2.io.net.unixsocket.UnixSockets
import org.http4s.client.middleware.UnixSocket
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder

val localSocket = Files[IO].tempFile(None, "", ".sock", None)
  .map(path => UnixSocketAddress(path.toString))

def server(socket: UnixSocketAddress) = EmberServerBuilder
  .default[IO]
  .withUnixSocketConfig(UnixSockets[IO], socket) // bind to a domain socket
  .withHttpApp(service.orNotFound)
  .withShutdownTimeout(1.second)
  .build
  .evalTap(_ => IO.sleep(4.seconds))

def client(socket: UnixSocketAddress) = EmberClientBuilder
  .default[IO]
  .build
  .map(UnixSocket[IO](socket)) // apply the middleware
```

```scala mdoc
localSocket.flatMap(socket => server(socket) *> client(socket))
  .use(cl => cl.status(Request[IO](uri = uri"/ok")))
  .unsafeRunSync()
```

[CookieJar]: @API_URL@org/http4s/client/middleware/CookieJar$.html

[FollowRedirect]: @API_URL@org/http4s/client/middleware/FollowRedirect$.html

[Retry]: @API_URL@org/http4s/client/middleware/Retry$.html

[RetryPolicy]: @API_URL@org/http4s/client/middleware/RetryPolicy$.html

[Unix domain sockets]: https://en.wikipedia.org/wiki/Unix_domain_socket
