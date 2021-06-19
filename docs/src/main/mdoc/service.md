---
menu: main
weight: 100
title: Service
---

This tutorial will walk you through creating your first http4s service
and calling it with http4s' client.

Create a new directory, with the following build.sbt in the root:

```scala
scalaVersion := "2.13.4" // Also supports 2.12.x

val http4sVersion = "{{< version "http4s.doc" >}}"

// Only necessary for SNAPSHOT releases
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-ember-client" % http4sVersion
)

// Uncomment if you're using Scala 2.12.x
// scalacOptions ++= Seq("-Ypartial-unification")
```

This tutorial is compiled as part of the build using [mdoc].  Each page
is its own REPL session.  If you copy and paste code samples starting
from the top, you should be able to follow along in a REPL.

```
$ sbt console
```

## Your first service

An `HttpRoutes[F]` is a simple alias for
`Kleisli[OptionT[F, *], Request, Response]`.  If that's meaningful to you,
great.  If not, don't panic: `Kleisli` is just a convenient wrapper
around a `Request => F[Response]`, and `F` is an effectful
operation.  We'll teach you what you need to know as we go, or if you
prefer you can read these introductions first:

* [cats-effect: The IO Monad for Scala]
* [Cats Kleisli Datatype]

### Defining your service

Wherever you are in your studies, let's create our first
`HttpRoutes`.  Start by pasting these imports into your SBT console:

```scala mdoc:silent
import cats.effect._, org.http4s._, org.http4s.dsl.io._, scala.concurrent.ExecutionContext.Implicits.global
```

You also will need a `ContextShift` and a `Timer`.  These come for
free if you are in an `IOApp`.

```scala mdoc:silent
implicit val cs: ContextShift[IO] = IO.contextShift(global)
implicit val timer: Timer[IO] = IO.timer(global)
```

Using the [http4s-dsl], we can construct an `HttpRoutes` by pattern
matching the request.  Let's build a service that matches requests to
`GET /hello/:name`, where `:name` is a path parameter for the person to
greet.

```scala mdoc
val helloWorldService = HttpRoutes.of[IO] {
  case GET -> Root / "hello" / name =>
    Ok(s"Hello, $name.")
}
```

### Returning content in the response
In order to return content of type `T` in the response an `EntityEncoder[T]`
must be used. We can define the `EntityEncoder[T]` implictly so that it
doesn't need to be explicitly included when serving the response.

In the example below, we're defining a `tweetEncoder` and then
explicitly using it to encode the response contents of a `Tweet`, which can
be seen as `Ok(getTweet(tweetId))(tweetEncoder)`.

We've defined `tweetsEncoder` as being implicit so that we don't need to explicitly
reference it when serving the response, which can be seen as
`getPopularTweets().flatMap(Ok(_))`.

```scala mdoc
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

### Running your service

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
import org.http4s.ember.server._
import org.http4s.implicits._
import org.http4s.server.Router
```

```scala mdoc
val services = tweetService <+> helloWorldService
val httpApp = Router("/" -> helloWorldService, "/api" -> services).orNotFound
val serverBuilder = EmberServerBuilder.default[IO].withHost("localhost").withPort(8082).withHttpApp(httpApp)
```

The `withHttpApp` call isn't strictly necessary as the server will be set to run
using defaults of port 8080 and the loopback address. The `withHttpApp` call
associates the specified routes with this http server instance.

We start a server resource in the background.  The server will run until we cancel the fiber:

```scala mdoc
val fiber = serverBuilder.build.use(_ => IO.never).start.unsafeRunSync()
```

Use curl, or your favorite HTTP client, to see your service in action:

```sh
$ curl http://localhost:8082/hello/Pete
```

## Cleaning up

We can shut down the server by canceling its fiber.

```scala mdoc
fiber.cancel.unsafeRunSync()
```

### Running your service as an `App`

As a convenience, cats-effect provides an `cats.effect.IOApp` trait
with an abstract `run` method that returns a `IO[ExitCode]`.  An
`IOApp` runs the process and adds a JVM shutdown hook to interrupt
the infinite process and gracefully shut down your server when a
SIGTERM is received.

You create the server within an `IOApp` using resource:

```scala mdoc:silent:reset
import cats.effect._
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.ember.server._
```

```scala mdoc:silent
object Main extends IOApp {

  val helloWorldService = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name.")
  }.orNotFound

  def run(args: List[String]): IO[ExitCode] =
    EmberServerBuilder.default[IO]
      .withHost("locahost")
      .withPort(8080)
      .withHttpApp(helloWorldService)
      .build
      .use(_ => Async[IO].never)
      .as(ExitCode.Success)
}
```

[ember]: https://github.com/http4s/http4s
[mdoc]: https://scalameta.org/mdoc/
[Cats Kleisli Datatype]: https://typelevel.org/cats/datatypes/kleisli.html
[cats-effect: The IO Monad for Scala]: https://typelevel.org/cats-effect/
[http4s-dsl]: ../dsl
