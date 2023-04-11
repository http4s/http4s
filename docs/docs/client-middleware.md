# Client middleware

Client middleware wraps a [Client] to add functionality. This document is a
list of included client middleware, which can be found
in the `org.http4s.client.middleware` package.

First we prepare a server that we can make requests to:
```scala mdoc:silent
import cats.effect._
import cats.syntax.all._
import org.typelevel.ci._
import org.http4s._
import org.http4s.headers._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.client.Client
import cats.effect.unsafe.IORuntime
import scala.concurrent.duration._
import cats.effect.std.Console

implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

val service = HttpRoutes.of[IO] {
  case r @ GET -> Root / "count" =>
    val count = r.cookies.find(_.name == "count")
      .flatMap(_.content.toIntOption).getOrElse(0) + 1
    Ok(s"count: $count").map(_.addCookie("count", count.toString))
  case GET -> Root / "redirect" => MovedPermanently(Location(uri"/ok"))
  case GET -> Root / "ok" => Ok("👍")
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
In this example the service will always increment a `count` cookie and set it in response.

```scala mdoc:silent
import org.http4s.client.middleware.CookieJar

// the domain is necessary because cookies are specific to a domain
val countRequest = Request[IO](Method.GET, uri"http://example.com/count")
```
```scala mdoc
// consecutive requests without persisting cookies always get the same value back
client.expect[String](countRequest).unsafeRunSync()
client.expect[String](countRequest).unsafeRunSync()
// if the client supports cookies then the value will increase
CookieJar.impl(client).flatMap { cl =>
    cl.expect[String](countRequest) >>
    cl.expect[String](countRequest) >>
    cl.expect[String](countRequest)
}.unsafeRunSync()
```

## DestinationAttribute
This very simple middleware simply writes a value to the request attributes, which can be
read at any point during the processing of the request, by other middleware down the line.

In this example we create our own middleware that appends an header to the response. We use
`DestinationAttribute` to provide the value.

```scala mdoc:silent
import org.http4s.client.middleware.DestinationAttribute
import org.http4s.client.middleware.DestinationAttribute.Destination

def myMiddleware(cl: Client[IO]): Client[IO] = Client { req =>
  val destination = req.attributes.lookup(Destination).getOrElse("")
  cl.run(req).map(_.putHeaders("X-Destination" -> destination ))
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

## GZip
Adds support for gzip compression. The client will indicate it can read gzip responses
and if the server responds with gzip, the client will decode the response transparently.

```scala mdoc:silent
import org.http4s.client.middleware.GZip
import org.http4s.server.middleware.{GZip => ServerGZip}

val gzipService = ServerGZip(
  HttpRoutes.of[IO] {
    case GET -> Root / "long" => Ok("0123456789" * 10) // the body has 100 bytes
  }
).orNotFound
val clientWithoutGzip = Client.fromHttpApp(gzipService)
val clientWithGzip = GZip()(clientWithoutGzip)
val longRequest = Request[IO](Method.GET, uri"/long")
```

```scala mdoc
// if we request gzip compression the body is smaller
// than the 100 bytes our route is sending
clientWithoutGzip
  .run(longRequest.putHeaders("Accept-Encoding" -> "gzip"))
  .use(_.body.compile.count).unsafeRunSync()
// when the client supports gzip we get the uncompressed body
clientWithGzip.run(longRequest).use(_.body.compile.count).unsafeRunSync()
```

## Logger, ResponseLogger, RequestLogger
Log requests and responses. `ResponseLogger` logs the responses, `RequestLogger`
logs the request, `Logger` logs both.

```scala mdoc:silent
import org.http4s.client.middleware.Logger

val loggerClient = Logger[IO](
  logHeaders = false,
  logBody = true,
  redactHeadersWhen = _ => false,
  logAction = Some((msg: String) => Console[IO].println(msg))
)(client)

```
```scala mdoc
loggerClient.expect[Unit](Request[IO](Method.GET, uri"/ok")).unsafeRunSync()
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
flakyService.flatMap { service =>
    val client = Client.fromHttpApp(service.orNotFound)
    val retryClient = Retry(policy)(client)
    retryClient.expect[String](Request[IO](uri = uri"/"))
} .unsafeRunSync()
```

## UnixSocket
[Domain sockets] are an operating system feature which allows comunication between processes
while not needing to use the network.

This middleware allows a client to make requests to a domain socket.
Docker uses domain sockets for communicating, for exemple between the docker daemon and the
command-line application. The following example, which can be run with `scala-cli`,
will list the docker containers on your machine.
```scala
//> using lib "org.typelevel::toolkit::0.0.5"

import cats.effect.*
import io.circe.*
import org.http4s.ember.client.*
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.circe.*
import org.http4s.client.middleware.UnixSocket
import fs2.io.net.unixsocket.UnixSocketAddress

object Main extends IOApp.Simple {
  def run = EmberClientBuilder.default[IO].build.use { client =>
    val socketClient = UnixSocket[IO](UnixSocketAddress("/var/run/docker.sock"))(client)
    val request = Request[IO](Method.GET, uri"http://localhost/containers/json")

    socketClient.expect[Json](request)
      .flatMap(IO.println)
  }
}
```

[CookieJar]: @API_URL@org/http4s/client/middleware/CookieJar$.html
[FollowRedirect]: @API_URL@org/http4s/client/middleware/FollowRedirect$.html
[Retry]: @API_URL@org/http4s/client/middleware/Retry$.html
[RetryPolicy]: @API_URL@org/http4s/client/middleware/RetryPolicy$.html
[Domain Sockets]: https://en.wikipedia.org/wiki/Unix_domain_socket
