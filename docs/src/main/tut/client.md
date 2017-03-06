---
menu: tut
weight: 200
title: HTTP Client
---

How do we know the server is running?  Let's create a client with
http4s to try our service.

The service again so tut picks it up:

```tut:book
import org.http4s.server.{Server, ServerApp}
import org.http4s.server.blaze._
import org.http4s._, org.http4s.dsl._

val service = HttpService {
  case GET -> Root / "hello" / name =>
    Ok(s"Hello, $name.")
}

import org.http4s.server.syntax._
val builder = BlazeBuilder.bindHttp(8080, "localhost").mountService(service, "/")
val server = builder.run
```

### Creating the client

A good default choice is the `PooledHttp1Client`.  As the name
implies, the `PooledHttp1Client` maintains a connection pool and
speaks HTTP 1.x.

```tut:book
import org.http4s.client.blaze._

val httpClient = PooledHttp1Client()
```

### Describing a call

To execute a GET request, we can call `expect` with the type we expect
and the URI we want:

```tut:book
val helloJames = httpClient.expect[String]("http://localhost:8080/hello/James")
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
import fs2.Task
import fs2.interop.cats._
import cats._
import cats.implicits._
import org.http4s.Uri

def hello(name: String): Task[String] = {
  val target = Uri.uri("http://localhost:8080/hello/") / name
  httpClient.expect[String](target)
}

val people = Vector("Michael", "Jessica", "Ashley", "Christopher")

val greetingList = people.map(hello).sequence
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
val greetingsStringTask = greetingList.map(_.mkString("\n"))
greetingsStringTask.unsafeRun
```

## Cleaning up

Our client consumes system resources. Let's clean up after ourselves by shutting
it down:

```tut:book
httpClient.shutdownNow()
```

```tut:book:invisible
server.shutdownNow()
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

[entity]: ../entity
[json]: ../json
