
# Streaming

Streaming lies at the heart of the http4s model of HTTP, in the literal sense that `EntityBody[F]`
is just a type alias for `Stream[F, Byte]`. Please see [entity] for details. This means
HTTP streaming is provided by both http4s' service support and its client support.


## Streaming responses from your service

Because `EntityBody[F]`s are streams anyway, returning a stream as a response from your service is
simplicity itself:

```scala mdoc:silent
import scala.concurrent.duration._

import cats.effect._
import fs2.Stream
import org.http4s._
import org.http4s.dsl.io._

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

## Consuming Streams with the Client

The http4s [client] supports consuming chunked HTTP responses as a stream, again because the
`EntityBody[F]` is a stream anyway. http4s' `Client` interface consumes streams with the `streaming`
function, which takes a `Request[F]` and a `Response[F] => Stream[F, A]` and returns a
`Stream[F, A]`. Since an `EntityBody[F]` is just a `Stream[F, Byte]`, then, the easiest way
to consume a stream is just:

```scala
client.stream(req).flatMap(_.body)
```

That gives you a `Stream[F, Byte]`, but you probably want something other than a `Byte`.
Here's some code intended to consume [Twitter's streaming APIs], which return a stream of JSON.

First, let's assume we want to use [Circe] for JSON support. Please see [json] for details.

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-circe" % "@VERSION@",
  "io.circe" %% "circe-generic" % "@CIRCE_VERSION@"
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

```scala mdoc:reset:silent
import org.http4s._
import org.http4s.ember.client._
import org.http4s.client.oauth1
import org.http4s.client.oauth1.ProtocolParameter._
import org.http4s.implicits._
import cats.effect._
import fs2.Stream
import fs2.io.stdout
import fs2.text.{lines, utf8Encode}
import io.circe.Json
import org.typelevel.jawn.fs2._
import org.typelevel.jawn.Facade
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory

class TWStream[F[_]: Async] {
  // jawn-fs2 needs to know what JSON AST you want
  implicit val f: Facade[Json] = new io.circe.jawn.CirceSupportParser(None, false).facade

  implicit val loggerFactory: LoggerFactory[F] = Slf4jFactory.create[F]

  /* These values are created by a Twitter developer web app.
   * OAuth signing is an effect due to generating a nonce for each `Request`.
   */
  def sign(consumerKey: String, consumerSecret: String, 
           accessToken: String, accessSecret: String)
          (req: Request[F]): F[Request[F]] = {
    val consumer = Consumer(consumerKey, consumerSecret)
    val token    = Token(accessToken, accessSecret)
    oauth1.signRequest(
      req,
      consumer,
      Some(token),
      realm = None,
      timestampGenerator = Timestamp.now,
      nonceGenerator = Nonce.now,
    )
  }

  /* Create a http client, sign the incoming `Request[F]`, stream the `Response[IO]`, and
   * `parseJsonStream` the `Response[F]`.
   * `sign` returns a `F`, so we need to `Stream.eval` it to use a for-comprehension.
   */
  def jsonStream(consumerKey: String, consumerSecret: String, 
                 accessToken: String, accessSecret: String)
                (req: Request[F]): Stream[F, Json] =
    for {
      client <- Stream.resource(EmberClientBuilder.default[F].build)
      sr  <- Stream.eval(sign(consumerKey, consumerSecret, accessToken, accessSecret)(req))
      res <- client.stream(sr).flatMap(_.body.chunks.parseJsonStream)
    } yield res

  /* Stream the sample statuses.
   * Plug in your four Twitter API values here.
   * We map over the Circe `Json` objects to pretty-print them with `spaces2`.
   * Then we `to` them to fs2's `lines` and then to `stdout` `Sink` to print them.
   */
  val stream: Stream[F, Unit] = {
    val req = Request[F](Method.GET, 
                uri"https://stream.twitter.com/1.1/statuses/sample.json")
    val s   = jsonStream("<consumerKey>", "<consumerSecret>",
                "<accessToken>", "<accessSecret>")(req)
    s.map(_.spaces2).through(lines).through(utf8Encode).through(stdout)
  }

  /** Compile our stream down to an effect to make it runnable */
  def run: F[Unit] =
    stream.compile.drain
}
```

`TWStream` runs any effect type supported by cats-effect.  We need to
pick a concrete effect, such as `cats.effect.IO`, to actually run it.
We'll make use of `cats.effect.IOApp`, which defines a program in terms
in the form `run(args: List[String]): IO[ExitCode]`:

```scala mdoc:silent
object TWStreamApp extends IOApp {
  def run(args: List[String]) =
    (new TWStream[IO]).run.as(ExitCode.Success)
}
```

[client]: client.md
[entity]: entity.md
[ScalaSyd 2015]: https://bitbucket.org/da_terry/scalasyd-doobie-http4s
[json]: json.md
[jawn]: https://github.com/non/jawn
[jawn-fs2]: https://github.com/typelevel/jawn-fs2
[Twitter's streaming APIs]: https://dev.twitter.com/streaming/overview
[circe]: https://circe.github.io/circe/
