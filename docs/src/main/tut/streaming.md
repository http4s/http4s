---
menu: main
title: Streaming
weight: 305
---

## Introduction

Streaming lies at the heart of the http4s model of HTTP, in the literal sense that `EntityBody[F]`
is just a type alias for `Stream[F, Byte]`. Please see [entity] for details. This means
HTTP streaming is provided by both http4s' service support and its client support.

## Streaming responses from your service

Because `EntityBody[F]`s are streams anyway, returning a stream as a response from your service is
simplicity itself:

```tut:silent
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import cats.effect._
import fs2.Stream
import org.http4s._
import org.http4s.dsl.io._

// Provided by `cats.effect.IOApp`, needed elsewhere:
implicit val timer: Timer[IO] = IO.timer(global)

// An infinite stream of the periodic elapsed time
val seconds = Stream.awakeEvery[IO](1.second)

val routes = HttpRoutes.of[IO] {
  case GET -> Root / "seconds" =>
    Ok(seconds.map(_.toString)) // `map` `toString` because there's no `EntityEncoder` for `Duration`
}
```

Streams are returned to the client as chunked HTTP responses automatically. You don't need to provide the header yourself.

For a more realistic example of streaming results from database queries to the client, please see the
[ScalaSyd 2015] example. In particular, if you want to stream JSON responses, please take note of how
it converts a stream of JSON objects to a JSON array, which is friendlier to clients.

## Consuming streams with the client

The http4s [client] supports consuming chunked HTTP responses as a stream, again because the
`EntityBody[F]` is a stream anyway. http4s' `Client` interface consumes streams with the `streaming`
function, which takes a `Request[F]` and a `Response[F] => Stream[F, A]` and returns a
`Stream[F, A]`. Since an `EntityBody[F]` is just a `Stream[F, Byte]`, then, the easiest way
to consume a stream is just:

```scala
client.stream(req).flatMap(_)
```

That gives you a `Stream[F, Byte]`, but you probably want something other than a `Byte`.
Here's some code intended to consume [Twitter's streaming APIs], which return a stream of JSON.

First, let's assume we want to use [Circe] for JSON support. Please see [json] for details.

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-circe" % "{{< version "http4s.doc" >}}",
  "io.circe" %% "circe-generic" % "{{< version circe >}}"
)
```

Next, we want _streaming_ JSON. Because http4s is streaming in its bones, it relies on [jawn] for
streaming JSON support. Most popular JSON libraries, including Circe, provide jawn support, as
the code below shows. What's left is to integrate jawn's streaming parsing with fs2's Stream.
That's done by [jawn-fs2], which http4s' Circe module depends on transitively.

Because Twitter's Streaming APIs literally return a stream of JSON objects, _not_ a JSON array,
we want to use jawn-fs2's `parseJsonStream`.

Finally, Twitter's Streaming APIs also require OAuth authentication. So our example is an OAuth
example as a bonus!

Putting it all together into a small app that will print the JSON objects forever:

```tut:silent
import org.http4s._
import org.http4s.client.blaze._
import org.http4s.client.oauth1
import cats.effect._
import cats.implicits._
import fs2.Stream
import fs2.io.stdout
import fs2.text.{lines, utf8Encode}
import io.circe.Json
import jawnfs2._
import java.util.concurrent.{Executors, ExecutorService}
import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global

class TWStream[F[_]](implicit F: ConcurrentEffect[F], cs: ContextShift[F]) {
  // jawn-fs2 needs to know what JSON AST you want
  implicit val f = io.circe.jawn.CirceSupportParser.facade

  /* These values are created by a Twitter developer web app.
   * OAuth signing is an effect due to generating a nonce for each `Request`.
   */
  def sign(consumerKey: String, consumerSecret: String, accessToken: String, accessSecret: String)
          (req: Request[F]): F[Request[F]] = {
    val consumer = oauth1.Consumer(consumerKey, consumerSecret)
    val token    = oauth1.Token(accessToken, accessSecret)
    oauth1.signRequest(req, consumer, callback = None, verifier = None, token = Some(token))
  }

  /* Create a http client, sign the incoming `Request[F]`, stream the `Response[IO]`, and
   * `parseJsonStream` the `Response[F]`.
   * `sign` returns a `F`, so we need to `Stream.eval` it to use a for-comprehension.
   */
  def jsonStream(consumerKey: String, consumerSecret: String, accessToken: String, accessSecret: String)
            (req: Request[F]): Stream[F, Json] =
    for {
      client <- BlazeClientBuilder(global).stream
      sr  <- Stream.eval(sign(consumerKey, consumerSecret, accessToken, accessSecret)(req))
      res <- client.stream(sr).flatMap(_.body.chunks.parseJsonStream)
    } yield res

  /* Stream the sample statuses.
   * Plug in your four Twitter API values here.
   * We map over the Circe `Json` objects to pretty-print them with `spaces2`.
   * Then we `to` them to fs2's `lines` and then to `stdout` `Sink` to print them.
   */
  def stream(blockingEC: ExecutionContext): Stream[F, Unit] = {
    val req = Request[F](Method.GET, Uri.uri("https://stream.twitter.com/1.1/statuses/sample.json"))
    val s   = jsonStream("<consumerKey>", "<consumerSecret>", "<accessToken>", "<accessSecret>")(req)
    s.map(_.spaces2).through(lines).through(utf8Encode).through(stdout(blockingEC))
  }

  /**
   * We're going to be writing to stdout, which is a blocking API.  We don't
   * want to block our main threads, so we create a separate pool.  We'll use
   * `fs2.Stream` to manage the shutdown for us.
   */
  def blockingEcStream: Stream[F, ExecutionContext] =
    Stream.bracket(F.delay(Executors.newFixedThreadPool(4)))(pool =>
        F.delay(pool.shutdown()))
      .map(ExecutionContext.fromExecutorService)

  /** Compile our stream down to an effect to make it runnable */
  def run: F[Unit] =
    blockingEcStream.flatMap { blockingEc =>
      stream(blockingEc)
    }.compile.drain
}
```

`TWStream` runs any effect type supported by cats-effect.  We need to
pick a concrete effect, such as `cats.effect.IO`, to actually run it.
We'll make use of `cats.effect.IOApp`, which defines a program in terms
in the form `run(args: List[String]): IO[ExitCode]`:

```tut:silent
object TWStreamApp extends IOApp {
  def run(args: List[String]) =
    (new TWStream[IO]).run.as(ExitCode.Success)
}
```

[client]: ../client
[entity]: ../entity
[ScalaSyd 2015]: https://bitbucket.org/da_terry/scalasyd-doobie-http4s
[json]: ../json
[jawn]: https://github.com/non/jawn
[jawn-fs2]: https://github.com/rossabaker/jawn-fs2
[Twitter's streaming APIs]: https://dev.twitter.com/streaming/overview
[circe]: https://circe.github.io/circe/
