---
menu: main
weight: 100
title: Service
---

This tutorial will walk you through creating your first http4s service
and calling it with http4s' client.

Create a new directory, with the following build.sbt in the root:

```scala
scalaVersion := "2.12.8" // Also supports 2.11.x

val http4sVersion = "{{< version "http4s.doc" >}}"

// Only necessary for SNAPSHOT releases
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion
)

scalacOptions ++= Seq("-Ypartial-unification")
```

This tutorial is compiled as part of the build using [tut].  Each page
is its own REPL session.  If you copy and paste code samples starting
from the top, you should be able to follow along in a REPL.

```
$ sbt console
```

## Your first service

An `HttpRoutes[F]` is a simple alias for
`Kleisli[OptionT[F, ?], Request, Response]`.  If that's meaningful to you,
great.  If not, don't panic: `Kleisli` is just a convenient wrapper
around a `Request => F[Response]`, and `F` is an effectful
operation.  We'll teach you what you need to know as we go, or if you
prefer you can read these introductions first:

* [cats-effect: The IO Monad for Scala]
* [Cats Kleisli Datatype]

### Defining your service

Wherever you are in your studies, let's create our first
`HttpRoutes`.  Start by pasting these imports into your SBT console:

```tut:silent
import cats.effect._, org.http4s._, org.http4s.dsl.io._, scala.concurrent.ExecutionContext.Implicits.global
```

You also will need a `ContextShift` and a `Timer`.  These come for
free if you are in an `IOApp`.

```tut:silent
implicit val cs: ContextShift[IO] = IO.contextShift(global)
implicit val timer: Timer[IO] = IO.timer(global)
```

Using the [http4s-dsl], we can construct an `HttpRoutes` by pattern
matching the request.  Let's build a service that matches requests to
`GET /hello/:name`, where `:name` is a path parameter for the person to
greet.

```tut:book
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

```tut:book
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
[blaze], the native backend supported by http4s.

We start from a `BlazeServerBuilder`, and then mount the `helloWorldService` under
the base path of `/` and the remainder of the services under the base
path of `/api`. The services can be mounted in any order as the request will be
matched against the longest base paths first. The `BlazeServerBuilder` is immutable
with chained methods, each returning a new builder.

Multiple `HttpRoutes` can be combined with the `combineK` method (or its alias
`<+>`) by importing `cats.implicits._` and `org.http4s.implicits._`. Please ensure partial unification is enabled in your `build.sbt`. 

`scalacOptions ++= Seq("-Ypartial-unification")`

```tut:silent
import cats.implicits._
import org.http4s.server.blaze._
import org.http4s.implicits._
import org.http4s.server.Router
```

```tut:book
val services = tweetService <+> helloWorldService
val httpApp = Router("/" -> helloWorldService, "/api" -> services).orNotFound
val serverBuilder = BlazeServerBuilder[IO].bindHttp(8080, "localhost").withHttpApp(httpApp)
```

The `bindHttp` call isn't strictly necessary as the server will be set to run
using defaults of port 8080 and the loopback address. The `withHttpApp` call
associates the specified routes with this http server instance.

We start a server resource in the background.  The server will run until we cancel the fiber:

```tut:book
val fiber = serverBuilder.resource.use(_ => IO.never).start.unsafeRunSync()
```

Use curl, or your favorite HTTP client, to see your service in action:

```sh
$ curl http://localhost:8080/hello/Pete
```

## Cleaning up

We can shut down the server by canceling its fiber.

```tut:book
fiber.cancel.unsafeRunSync()
```

### Running your service as an `App`

Every `ServerBuilder[F]` has a `.serve` method that returns a
`Stream[F, ExitCode]`.  This stream runs forever without emitting
any output.  When this process is run with `.unsafeRunSync` on the
main thread, it blocks forever, keeping the JVM (and your server)
alive until the JVM is killed.

As a convenience, cats-effect provides an `cats.effect.IOApp` trait
with an abstract `run` method that returns a `IO[ExitCode]`.  An
`IOApp` runs the process and adds a JVM shutdown hook to interrupt
the infinite process and gracefully shut down your server when a
SIGTERM is received.

```tut:silent:reset
import cats.effect._
import cats.implicits._
import org.http4s.HttpRoutes
import org.http4s.syntax._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze._
```

```tut:book
object Main extends IOApp {

  val helloWorldService = HttpRoutes.of[IO] {
    case GET -> Root / "hello" / name =>
      Ok(s"Hello, $name.")
  }.orNotFound

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(helloWorldService)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
}
```

You may also create the server within an `IOApp` using resource:

```tut:book
object MainWithResource extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(Main.helloWorldService)
      .resource
      .use(_ => IO.never)
      .as(ExitCode.Success)
}
```

[blaze]: https://github.com/http4s/blaze
[tut]: https://github.com/tpolecat/tut
[Cats Kleisli Datatype]: https://typelevel.org/cats/datatypes/kleisli.html
[cats-effect: The IO Monad for Scala]: https://typelevel.org/cats-effect/
[http4s-dsl]: ../dsl
