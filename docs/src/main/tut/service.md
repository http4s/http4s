---
menu: main
weight: 100
title: Service
---

This tutorial will walk you through creating your first http4s service
and calling it with http4s' client.

Create a new directory, with the following build.sbt in the root:

```scala
scalaVersion := "2.11.8" // Also supports 2.10.x and 2.12.x

val http4sVersion = "{{< version "http4s.doc" >}}"

// Only necessary for SNAPSHOT releases
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion
)
```

This tutorial is compiled as part of the build using [tut].  Each page
is its own REPL session.  If you copy and paste code samples starting
from the top, you should be able to follow along in a REPL.

```
$ sbt console
```

## Your first service

An `HttpService` is a simple alias for
`Kleisli[Task, Request, Response]`.  If that's meaningful to you,
great.  If not, don't panic: `Kleisli` is just a convenient wrapper
around a `Request => Task[Response]`, and `Task` is an asynchronous
operation.  We'll teach you what you need to know as we go, or you
can, uh, fork a task to read these introductions first:

* [Scalaz Task: The Missing Documentation]
* [Kleisli: Composing monadic functions]

### Defining your service

Wherever you are in your studies, let's create our first
`HttpService`.  Start by pasting these imports into your SBT console:

```tut:book
import org.http4s._, org.http4s.dsl._
```

Using the [http4s-dsl], we can construct an `HttpService` by pattern
matching the request.  Let's build a service that matches requests to
`GET /hello/:name`, where `:name` is a path parameter for the person to
greet.

```tut:book
val helloWorldService = HttpService {
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
`Ok(getPopularTweets())`.

```tut:book
import scalaz.concurrent.Task

case class Tweet(id: Int, message: String)

def tweetEncoder: EntityEncoder[Tweet] = ???
implicit def tweetsEncoder: EntityEncoder[Seq[Tweet]] = ???

def getTweet(tweetId: Int): Task[Tweet] = ???
def getPopularTweets(): Task[Seq[Tweet]] = ???

val tweetService = HttpService {
  case request @ GET -> Root / "tweets" / "popular" =>
    Ok(getPopularTweets())
  case request @ GET -> Root / "tweets" / IntVar(tweetId) =>
    getTweet(tweetId).flatMap(Ok(_)(tweetEncoder))
}
```

### Running your service

http4s supports multiple server backends.  In this example, we'll use
[blaze], the native backend supported by http4s.

We start from a `BlazeBuilder`, and then mount the `helloWorldService` under
the base path of `/` and the remainder of the services under the base
path of `/api`. The services can be mounted in any order as the request will be
matched against the longest base paths first. The `BlazeBuilder` is immutable
with chained methods, each returning a new builder.

Multiple `HttpService`s can be combined with the `orElse` method by importing
`org.http4s.server.syntax._`.

```tut:book
import org.http4s.server.blaze._
import org.http4s.server.syntax._

val services = tweetService orElse helloWorldService
val builder = BlazeBuilder.bindHttp(8080, "localhost").mountService(helloWorldService, "/").mountService(services, "/api")
```

The `bindHttp` call isn't strictly necessary as the server will be set to run
using defaults of port 8080 and the loopback address. The `mountService` call
associates a base path with a `HttpService`.

A builder can be `run` to start the server.

```tut:book
val server = builder.run
```

Use curl, or your favorite HTTP client, to see your service in action:

```sh
$ curl http://localhost:8080/hello/Pete
```

## Cleaning up

Our server consumes system resources. Let's clean up by shutting it
down:

```tut:book
server.shutdownNow()
```

### Running your service as an `App`

Every `ServerBuilder` has a `.serve` method that returns a
`Process[Task, Nothing]`.  This process runs forever without emitting
any output.  When this process is run with `.unsafePerformSync` on the
main thread, it blocks forever, keeping the JVM (and your server)
alive until the JVM is killed.

As a convenience, http4s provides an `org.http4s.util.ProcessApp` trait
with an abstract `main` method that returns a `Process`.  A `ProcessApp`
runs the process and adds a JVM shutdown hook to interrupt the infinite
process and gracefully shut down your server when a SIGTERM is received.

```tut:book
import org.http4s.server.blaze._
import org.http4s.util.ProcessApp
import scalaz.concurrent.Task
import scalaz.stream.Process

object Main extends ProcessApp {
  override def process(args: List[String]): Process[Task, Nothing] = {
    BlazeBuilder
      .bindHttp(8080, "localhost")
      .mountService(helloWorldService, "/")
      .mountService(services, "/api")
      .serve
  }
}
```

[blaze]: https://github.com/http4s/blaze
[tut]: https://github.com/tpolecat/tut
[Kleisli: Composing monadic functions]: http://eed3si9n.com/learning-scalaz/Composing+monadic+functions.html
[Scalaz Task: The Missing Documentation]: http://timperrett.com/2014/07/20/scalaz-task-the-missing-documentation/
[http4s-dsl]: ../dsl
