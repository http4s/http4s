---
layout: default
title: Getting started
---

This tutorial will walk you through creating your first http4s service
and calling it with http4s' client.

Create a new directory, with the following build.sbt in the root:

```scala
scalaVersion := "2.11.8" // Also supports 2.10.x

lazy val http4sVersion = "0.14.1"

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

Using the http4s-dsl, we can construct an `HttpService` by pattern
matching the request.  Let's build a service that matches requests to
`GET /hello/:name`, where `:name` is a path parameter for the person to
greet.

```tut:book
val service = HttpService {
  case GET -> Root / "hello" / name =>
    Ok(s"Hello, $name.")
}
```

### Running your service

http4s supports multiple server backends.  In this example, we'll use
[blaze], the native backend supported by http4s.

We start from a `BlazeBuilder`, and then mount a service.  The
`BlazeBuilder` is immutable with chained methods, each returning
a new builder.

```tut:book
import org.http4s.server.blaze._

val builder = BlazeBuilder.mountService(service)
```

A builder can be `run` to start the server.  By default, http4s
servers bind to port 8080.

```tut:book
val server = builder.run
```

## Your first client

How do we know the server is running?  Let's create a client with
http4s to try our service.

### Creating the client

A good default choice is the `PooledHttp1Client`.  As the name
implies, the `PooledHttp1Client` maintains a connection pool and
speaks HTTP 1.x.

```tut:book
import org.http4s.client.blaze._

val client = PooledHttp1Client()
```

### Describing a call

To execute a GET request, we can call `getAs` with the type we expect
and the URI we want:

```tut:book
val helloJames = client.getAs[String]("http://localhost:8080/hello/James")
```

Note that we don't have any output yet.  We have a `Task[String]`, to
represent the asynchronous nature of a client request.

Furthermore, we haven't even executed the request yet.  A significant
difference between a `Task` and a `scala.concurrent.Future` is that a
`Future` starts running immediately on its implicit execution context,
whereas a `Task` runs when it's told.  Executing a request is an
example of a side effect.  In functional programming, we prefer to
build a description of the program we're going to run, and defer its
side effects to the end.

Let's describe how we're going to greet a collection of people in
parallel:

```tut:book
import scalaz.concurrent.Task

def hello(name: String): Task[String] = {
  val target = uri("http://localhost:8080/hello/") / name
  client.getAs[String](target)
}

val people = Vector("Michael", "Jessica", "Ashley", "Christopher")

val greetingList = Task.gatherUnordered(people.map(hello))
```

Observe how simply we could combine a single `Task[String]` returned
by `hello` into a scatter-gather to return a `Task[List[String]]`.

## Making the call

It is best to run your `Task` "at the end of the world."  The "end of
the world" varies by context:

* In a command line app, it's your main method.
* In an `HttpService`, a `Task[Response]` is returned to be run by the
  server.
* Here in the REPL, the last line is the end of the world.  Here we go:

```tut:book
greetingList.run.mkString("\n")
```

## Cleaning up

Both our client and our server consume system resources.  Let's clean
up after ourselves by shutting each down:

```tut:book
client.shutdownNow()
server.shutdownNow()
```

[blaze]: https://github.com/http4s/blaze
[tut]: https://github.com/tpolecat/tut
[Kleisli: Composing monadic functions]: http://eed3si9n.com/learning-scalaz/Composing+monadic+functions.html
[Scalaz Task: The Missing Documentation]: http://timperrett.com/2014/07/20/scalaz-task-the-missing-documentation/
