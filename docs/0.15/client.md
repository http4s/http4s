---
layout: default
title: HTTP Client
---

## Your first client

How do we know the server is running?  Let's create a client with
http4s to try our service.

The service again so tut picks it up:

```scala
import org.http4s.server.{Server, ServerApp}
// import org.http4s.server.{Server, ServerApp}

import org.http4s.server.blaze._
// import org.http4s.server.blaze._

import org.http4s._, org.http4s.dsl._
// import org.http4s._
// import org.http4s.dsl._

val service = HttpService {
  case GET -> Root / "hello" / name =>
    Ok(s"Hello, $name.")
}
// service: org.http4s.HttpService = Kleisli(<function1>)

import org.http4s.server.syntax._
// import org.http4s.server.syntax._

val builder = BlazeBuilder.bindHttp(8080, "localhost").mountService(service, "/")
// builder: org.http4s.server.blaze.BlazeBuilder = org.http4s.server.blaze.BlazeBuilder@33ca02f7

val server = builder.run
// server: org.http4s.server.Server = BlazeServer(/127.0.0.1:8080)
```


### Creating the client

A good default choice is the `PooledHttp1Client`.  As the name
implies, the `PooledHttp1Client` maintains a connection pool and
speaks HTTP 1.x.

```scala
import org.http4s.client.blaze._
// import org.http4s.client.blaze._

val httpClient = PooledHttp1Client()
// httpClient: org.http4s.client.Client = Client(Kleisli(<function1>),scalaz.concurrent.Task@288fbe7a)
```

### Describing a call

To execute a GET request, we can call `getAs` with the type we expect
and the URI we want:

```scala
val helloJames = httpClient.expect[String]("http://localhost:8080/hello/James")
// helloJames: scalaz.concurrent.Task[String] = scalaz.concurrent.Task@13e885e4
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
  httpClient.expect[String](target)
}
// hello: (name: String)scalaz.concurrent.Task[String]

val people = Vector("Michael", "Jessica", "Ashley", "Christopher")
// people: scala.collection.immutable.Vector[String] = Vector(Michael, Jessica, Ashley, Christopher)

val greetingList = Task.gatherUnordered(people.map(hello))
// greetingList: scalaz.concurrent.Task[List[String]] = scalaz.concurrent.Task@642796ca
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
// <console>:35: warning: method run in class Task is deprecated: use unsafePerformSync
//        greetingList.run.mkString("\n")
//                     ^
// res0: String =
// Hello, Jessica.
// Hello, Christopher.
// Hello, Ashley.
// Hello, Michael.
```

## Cleaning up

Our client consumes system resources. Let's clean up after ourselves by shutting
it down:

```scala
httpClient.shutdownNow()
```




## Calls to a JSON API

Take a look at [json].

## Body decoding / encoding

The reusable way to decode/encode a request is to write a custom `EntityDecoder`
and `EntityEncoder`. For that topic, take a look at [entity].

If you prefer the quick & dirty solution, some of the methods take a `Response
=> Task[A]` argument, which lets you add a function which includes the decoding
functionality, but ignores the media type.

```scala
TODO: Example here
```

However, your function has to consume the body before the returned `Task` exits.
Don't do this:

```scala
// will come back to haunt you
client.get[EntityBody]("some-url")(response => response.body)
```

Passing it to a `EntityDecoder` is safe.

```
client.get[T]("some-url")(response => jsonOf(response.body))
```

[entity]: entity.md
[service]: service.html
[json]: json.html
