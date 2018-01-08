---
menu: main
weight: 200
title: HTTP Client
---

How do we know the server is running?  Let's create a client with
http4s to try our service.

A recap of the dependencies for this example, in case you skipped the [service] example. Ensure you have the following dependencies in your build.sbt:

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

Then we create the [service] again so tut picks it up:

```tut:book
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.server.blaze._

val service = HttpService[IO] {
  case GET -> Root / "hello" / name =>
    Ok(s"Hello, $name.")
}

val builder = BlazeBuilder[IO].bindHttp(8080, "localhost").mountService(service, "/").start
val server = builder.unsafeRunSync
```

### Creating the client

A good default choice is the `PooledHttp1Client`.  As the name
implies, the `PooledHttp1Client` maintains a connection pool and
speaks HTTP 1.x.

```tut:book
import org.http4s.client.blaze._

val httpClient = PooledHttp1Client[IO]()
```

### Describing a call

To execute a GET request, we can call `expect` with the type we expect
and the URI we want:

```tut:book
val helloJames = httpClient.expect[String]("http://localhost:8080/hello/James")
```

Note that we don't have any output yet.  We have a `IO[String]`, to
represent the asynchronous nature of a client request.

Furthermore, we haven't even executed the request yet.  A significant
difference between a `IO` and a `scala.concurrent.Future` is that a
`Future` starts running immediately on its implicit execution context,
whereas a `IO` runs when it's told.  Executing a request is an
example of a side effect.  In functional programming, we prefer to
build a description of the program we're going to run, and defer its
side effects to the end.

Let's describe how we're going to greet a collection of people in
parallel:

```tut:book
import cats._, cats.effect._, cats.implicits._
import org.http4s.Uri
import scala.concurrent.ExecutionContext.Implicits.global

def hello(name: String): IO[String] = {
  val target = Uri.uri("http://localhost:8080/hello/") / name
  httpClient.expect[String](target)
}

val people = Vector("Michael", "Jessica", "Ashley", "Christopher")

val greetingList = fs2.async.parallelTraverse(people)(hello)
```

Observe how simply we could combine a single `F[String]` returned
by `hello` into a scatter-gather to return a `F[List[String]]`.

## Making the call

It is best to run your `F` "at the end of the world."  The "end of
the world" varies by context:

* In a command line app, it's your main method.
* In an `HttpService[F]`, an `F[Response[F]]` is returned to be run by the
  server.
* Here in the REPL, the last line is the end of the world.  Here we go:

```tut:book
val greetingsStringEffect = greetingList.map(_.mkString("\n"))
greetingsStringEffect.unsafeRunSync
```

## Cleaning up

Our client consumes system resources. Let's clean up after ourselves by shutting
it down:

```tut:book
httpClient.shutdownNow()
```

```tut:book:invisible
server.shutdown.unsafeRunSync
```

## Calls to a JSON API

Take a look at [json].

## Body decoding / encoding

The reusable way to decode/encode a request is to write a custom `EntityDecoder`
and `EntityEncoder`. For that topic, take a look at [entity].

If you prefer a more fine-grained approach, some of the methods take a `Response[F]
=> F[A]` argument, such as `fetch` or `get`, which lets you add a function which includes the
decoding functionality, but ignores the media type.

```scala
client.fetch(req) {
  case Status.Successful(r) => r.attemptAs[A].leftMap(_.message).value
  case r => r.as[String]
    .map(b => Left(s"Request $req failed with status ${r.status.code} and body $b"))
}
```

However, your function has to consume the body before the returned `F` exits.
Don't do this:

```scala
// will come back to haunt you
client.get[EntityBody]("some-url")(response => response.body)
```

Passing it to a `EntityDecoder` is safe.

```
client.get[T]("some-url")(response => jsonOf(response.body))
```

[service]: ../service
[entity]: ../entity
[json]: ../json
