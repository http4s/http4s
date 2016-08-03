---
layout: default
title: Getting started
---

This tutorial will walk you through creating your first http4s service
and calling it with http4s' client.

Create a new directory, with the following build.sbt in the root:

```scala
scalaVersion := "2.11.8" // Also supports 2.10.x

lazy val http4sVersion = "0.15.0-SNAPSHOT"

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

```scala
import org.http4s._, org.http4s.dsl._
// import org.http4s._
// import org.http4s.dsl._
```

Using the http4s-dsl, we can construct an `HttpService` by pattern
matching the request.  Let's build a service that matches requests to
`GET /hello/:name`, where `:name` is a path parameter for the person to
greet.

```scala
val service = HttpService {
  case GET -> Root / "hello" / name =>
    Ok(s"Hello, $name.")
}
// service: org.http4s.HttpService = Kleisli(org.http4s.package$HttpService$$$Lambda$6392/5975614@5d5ea6a)
```

### Running your service

http4s supports multiple server backends.  In this example, we'll use
[blaze], the native backend supported by http4s.

We start from a `BlazeBuilder`, and then mount a service.  The
`BlazeBuilder` is immutable with chained methods, each returning
a new builder.

```scala
import org.http4s.server.blaze._
// import org.http4s.server.blaze._

val builder = BlazeBuilder.mountService(service)
// builder: org.http4s.server.blaze.BlazeBuilder = org.http4s.server.blaze.BlazeBuilder@6dacd290
```

A builder can be `run` to start the server.  By default, http4s
servers bind to port 8080.

```scala
val server = builder.run
// server: org.http4s.server.Server = BlazeServer(/127.0.0.1:8080)
```

## Your first client

How do we know the server is running?  Let's create a client with
http4s to try our service.

### Creating the client

A good default choice is the `PooledHttp1Client`.  As the name
implies, the `PooledHttp1Client` maintains a connection pool and
speaks HTTP 1.x.

```scala
import org.http4s.client.blaze._
// import org.http4s.client.blaze._

val client = PooledHttp1Client()
// client: org.http4s.client.Client = Client(Kleisli(org.http4s.client.blaze.BlazeClient$$$Lambda$6450/1404393338@4ee92260),scalaz.concurrent.Task@645e2b47)
```

### Describing a call

To execute a GET request, we can call `getAs` with the type we expect
and the URI we want:

```scala
val helloJames = client.expect[String]("http://localhost:8080/hello/James")
// helloJames: scalaz.concurrent.Task[String] = scalaz.concurrent.Task@13fab8b1
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

```scala
import scalaz.concurrent.Task
// import scalaz.concurrent.Task

import org.http4s.Uri
// import org.http4s.Uri

def hello(name: String): Task[String] = {
  val target = Uri.uri("http://localhost:8080/hello/") / name
  client.expect[String](target)
}
// hello: (name: String)scalaz.concurrent.Task[String]

val people = Vector("Michael", "Jessica", "Ashley", "Christopher")
// people: scala.collection.immutable.Vector[String] = Vector(Michael, Jessica, Ashley, Christopher)

val greetingList = Task.gatherUnordered(people.map(hello))
// greetingList: scalaz.concurrent.Task[List[String]] = scalaz.concurrent.Task@1c4e2dd5
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

```scala
greetingList.run.mkString("\n")
// <console>:31: warning: method run in class Task is deprecated: use unsafePerformSync
//        greetingList.run.mkString("\n")
//                     ^
// res0: String =
// Hello, Jessica.
// Hello, Ashley.
// Hello, Christopher.
// Hello, Michael.
```

## Cleaning up

Both our client and our server consume system resources.  Let's clean
up after ourselves by shutting each down:

```scala
client.shutdownNow()

server.shutdownNow()
```

### Next steps

Next, we'll take a deeper look at creating `HttpService`s with
[http4s-dsl].

[blaze]: https://github.com/http4s/blaze
[tut]: https://github.com/tpolecat/tut
[Kleisli: Composing monadic functions]: http://eed3si9n.com/learning-scalaz/Composing+monadic+functions.html
[Scalaz Task: The Missing Documentation]: http://timperrett.com/2014/07/20/scalaz-task-the-missing-documentation/
[http4s-dsl]: dsl.html
