# Service

This tutorial will walk you through creating your first http4s service
and calling it with http4s' client.

Create a new directory, with the following build.sbt in the root:

```scala
scalaVersion := "@SCALA213_VERSION@" // Also supports 2.12.x and 3.x

val http4sVersion = "@VERSION@"

// Only necessary for SNAPSHOT releases
resolvers += Resolver.sonatypeOssRepos("snapshots")

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
)

```

This tutorial is compiled as part of the build using [mdoc].  Each page
is its own REPL session.  If you copy and paste code samples starting
from the top, you should be able to follow along in a REPL.

```
$ sbt console
```

## Your First Service

An `HttpRoutes[F]` is a simple alias for
`Kleisli[OptionT[F, *], Request, Response]`.  If that's meaningful to you,
great.  If not, don't panic: `Kleisli` is just a convenient wrapper
around a `Request => F[Response]`, and `F` is an effectful
operation.  We'll teach you what you need to know as we go, or if you
prefer you can read these introductions first:

* [cats-effect: The IO Monad for Scala]
* [Cats Kleisli Datatype]

### Defining Your Service

Wherever you are in your studies, let's create our first
`HttpRoutes`.  Start by pasting these imports into your SBT console:

```scala mdoc:silent
import cats.effect._, org.http4s._, org.http4s.dsl.io._
```

If you're in a REPL, we also need a runtime.  This comes for free in `IOApp`:

```scala mdoc:silent
import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
```

Using the [http4s-dsl], we can construct an `HttpRoutes` by pattern
matching the request.  Let's build a service that matches requests to
`GET /hello/:name`, where `:name` is a path parameter for the person to
greet.

```scala mdoc:silent
val helloWorldService = HttpRoutes.of[IO] {
  case GET -> Root / "hello" / name =>
    Ok(s"Hello, $name.")
}
```

### Returning Content in the Response

In order to return content of type `T` in the response an `EntityEncoder[T]`
must be used. We can define the `EntityEncoder[T]` implictly so that it
doesn't need to be explicitly included when serving the response.

In the example below, we're defining a `tweetEncoder` and then
explicitly using it to encode the response contents of a `Tweet`, which can
be seen as `Ok(getTweet(tweetId))(tweetEncoder)`.

We've defined `tweetsEncoder` as being implicit so that we don't need to explicitly
reference it when serving the response, which can be seen as
`getPopularTweets().flatMap(Ok(_))`.

```scala mdoc:silent
case class Tweet(id: Int, message: String)

implicit def tweetEncoder: EntityEncoder[IO, Tweet] = ???
implicit def tweetsEncoder: EntityEncoder[IO, Seq[Tweet]] = ???

def getTweet(tweetId: Int): IO[Tweet] = ???
def getPopularTweets(): IO[Seq[Tweet]] = ???

val tweetService = HttpRoutes.of[IO] {
  case GET -> Root / "tweets" / "popular" =>
    getPopularTweets().flatMap(Ok(_))
  case GET -> Root / "tweets" / IntVar(tweetId) =>
    getTweet(tweetId).flatMap(Ok(_))
}
```

### Running Your Service

http4s supports multiple server backends.  In this example, we'll use
[ember], the native backend supported by http4s.

We start from a `EmberServerBuilder`, and then mount the `helloWorldService` under
the base path of `/` and the remainder of the services under the base
path of `/api`. The services can be mounted in any order as the request will be
matched against the longest base paths first. The `EmberServerBuilder` is immutable
with chained methods, each returning a new builder.

Multiple `HttpRoutes` can be combined with the `combineK` method (or its alias
`<+>`) by importing `cats.implicits._` and `org.http4s.implicits._`. Please ensure partial unification is enabled in your `build.sbt`.

`scalacOptions ++= Seq("-Ypartial-unification")`

```scala mdoc:silent
import cats.syntax.all._
import com.comcast.ip4s._
import org.http4s.ember.server._
import org.http4s.implicits._
import org.http4s.server.Router
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import scala.concurrent.duration._
```

```scala mdoc:silent
implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  
val services = tweetService <+> helloWorldService
val httpApp = Router("/" -> helloWorldService, "/api" -> services).orNotFound
val server = EmberServerBuilder
  .default[IO]
  .withHost(ipv4"0.0.0.0")
  .withPort(port"8080")
  .withHttpApp(httpApp)
  .build
```

The `withHttpApp` call associates the specified routes with this http server instance.

We start a server resource in the background.

```scala mdoc:silent
val shutdown = server.allocated.unsafeRunSync()._2
```

Use curl, or your favorite HTTP client, to see your service in action:

```sh
$ curl http://localhost:8080/hello/Pete
```

## Cleaning Up

We can shut down the server by canceling its fiber.

```scala mdoc:silent
shutdown.unsafeRunSync()
```

### Running Your Service as an `App`

As a convenience, cats-effect provides an `cats.effect.IOApp` trait
with an abstract `run` method that returns a `IO[ExitCode]`.  An
`IOApp` runs the process and adds a JVM shutdown hook to interrupt
the infinite process and gracefully shut down your server when a
SIGTERM is received.

```scala mdoc:silent:reset
import cats.effect._
import com.comcast.ip4s._
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.ember.server._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
```

```scala mdoc:silent
object Main extends IOApp {

  implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]

  val helloWorldService = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name.")
  }.orNotFound

  def run(args: List[String]): IO[ExitCode] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(helloWorldService)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)
}
```

[ember]: https://github.com/http4s/http4s
[mdoc]: https://scalameta.org/mdoc/
[Cats Kleisli Datatype]: https://typelevel.org/cats/datatypes/kleisli.html
[cats-effect: The IO Monad for Scala]: https://typelevel.org/cats-effect/
[http4s-dsl]: dsl.md
