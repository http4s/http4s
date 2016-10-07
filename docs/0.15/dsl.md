---
layout: default
title: The http4s DSL
---

Recall from earlier that an `HttpService` is just a type alias for
`Kleisli[Task, Request, Response]`.  This provides a minimal
foundation for declaring services and executing them on blaze or a
servlet container.  While this foundation is composeable, it is not
highly productive.  Most service authors will seek a higher level DSL.

## Add the http4s-dsl to your build

One option is the http4s-dsl.  It is officially supported by the
http4s team, but kept separate from core in order to encourage
multiple approaches for different needs.

This tutorial assumes that http4s-dsl is on your classpath.  Add the
following to your build.sbt:

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
)
```

All we need is a REPL to follow along at home:

```
$ sbt console
```

## The simplest service

We'll need the following imports to get started:

```scala
import org.http4s._, org.http4s.dsl._
// import org.http4s._
// import org.http4s.dsl._

import scalaz.concurrent.Task
// import scalaz.concurrent.Task
```

The central concept of http4s-dsl is pattern matching.  An
`HttpService` is declared as a simple series of case statements.  Each
case statement attempts to match and optionally extract from an
incoming `Request`.  The code associated with the first matching case
is used to generate a `Task[Response]`.

The simplest case statement matches all requests without extracting
anything.  The right hand side of the request must return a
`Task[Response]`.

```scala
val service = HttpService {
  case _ =>
    Task.delay(Response(Status.Ok))
}
// service: org.http4s.HttpService = Kleisli(<function1>)
```

## Testing the Service

One beautiful thing about the `HttpService` model is that we don't
need a server to test our route.  We can construct our own request
and experiment directly in the REPL.

```scala
scala> val getRoot = Request(Method.GET, uri("/"))
getRoot: org.http4s.Request = Request(method=GET, uri=/, headers=Headers()

scala> val task = service.run(getRoot)
task: scalaz.concurrent.Task[org.http4s.Response] = scalaz.concurrent.Task@12eb3731
```

Where is our `Response`?  It hasn't been created yet.  We wrapped it
in a `Task`.  In a real service, generating a `Response` is likely to
be an asynchronous operation with side effects, such as invoking
another web service or querying a database, or maybe both.  Operating
in a `Task` gives us control over the sequencing of operations and
lets us reason about our code like good functional programmers.  It is
the `HttpService`'s job to describe the task, and the server's job to
run it.

But here in the REPL, it's up to us to run it:

```scala
scala> val response = task.run
<console>:22: warning: method run in class Task is deprecated: use unsafePerformSync
       val response = task.run
                           ^
response: org.http4s.Response = Response(status=200, headers=Headers())
```

Cool.

## Generating responses

We'll circle back to more sophisticated pattern matching of requests,
but it will be a tedious affair until we learn a more succinct way of
generating `Task[Response]`s.

### Status codes

http4s-dsl provides a shortcut to create a `Task[Response]` by
applying a status code:

```scala
scala> val okTask = Ok()
okTask: scalaz.concurrent.Task[org.http4s.Response] = scalaz.concurrent.Task@7619e9d3

scala> val ok = okTask.run
<console>:20: warning: method run in class Task is deprecated: use unsafePerformSync
       val ok = okTask.run
                       ^
ok: org.http4s.Response = Response(status=200, headers=Headers())
```

This simple `Ok()` expression succinctly says what we mean in a
service:

```scala
HttpService {
  case _ => Ok()
}.run(getRoot).run
// <console>:23: warning: method run in class Task is deprecated: use unsafePerformSync
//        }.run(getRoot).run
//                       ^
// res0: org.http4s.Response = Response(status=200, headers=Headers())
```

This syntax works for other status codes as well.  In our example, we
don't return a body, so a `204 No Content` would be a more appropriate
response:

```scala
HttpService {
  case _ => NoContent()
}.run(getRoot).run
// <console>:23: warning: method run in class Task is deprecated: use unsafePerformSync
//        }.run(getRoot).run
//                       ^
// res1: org.http4s.Response = Response(status=204, headers=Headers())
```

### Responding with a body

#### Simple bodies

Most status codes take an argument as a body.  In http4s, `Request`
and `Response` bodies are represented as a
`scalaz.stream.Process[Task, ByteVector]`.  It's also considered good
HTTP manners to provide a `Content-Type` and, where known in advance,
`Content-Length` header in one's responses.

All of this hassle is neatly handled by http4s' [EntityEncoder]s.
We'll cover these in more depth in another tut.  The important point
for now is that a response body can be generated for any type with an
implicit `EntityEncoder` in scope.  http4s provides several out of the
box:

```scala
scala> Ok("Received request.").run
<console>:20: warning: method run in class Task is deprecated: use unsafePerformSync
       Ok("Received request.").run
                               ^
res2: org.http4s.Response = Response(status=200, headers=Headers(Content-Type: text/plain; charset=UTF-8, Content-Length: 17))

scala> import java.nio.charset.StandardCharsets.UTF_8
import java.nio.charset.StandardCharsets.UTF_8

scala> Ok("binary".getBytes(UTF_8)).run
<console>:21: warning: method run in class Task is deprecated: use unsafePerformSync
       Ok("binary".getBytes(UTF_8)).run
                                    ^
res3: org.http4s.Response = Response(status=200, headers=Headers(Content-Type: application/octet-stream, Content-Length: 6))
```

Per the HTTP specification, some status codes don't support a body.
http4s prevents such nonsense at compile time:

```scala
scala> NoContent("does not compile")
<console>:21: error: too many arguments for method apply: ()scalaz.concurrent.Task[org.http4s.Response] in trait EmptyResponseGenerator
       NoContent("does not compile")
                ^
```

#### Asynchronous responses

While http4s prefers `Task`, you may be working with libraries that
use standard library [Future]s.  Some relevant imports:

```scala
import scala.concurrent.Future
// import scala.concurrent.Future

import scala.concurrent.ExecutionContext.Implicits.global
// import scala.concurrent.ExecutionContext.Implicits.global
```

You can seamlessly respond with a `Future` of any type that has an
`EntityEncoder`.

```scala
scala> val task = Ok(Future {
     |   println("I run when the future is constructed.")
     |   "Greetings from the future!"
     | })
task: scalaz.concurrent.Task[org.http4s.Response] = scalaz.concurrent.Task@7c92355f

scala> task.run
<console>:24: warning: method run in class Task is deprecated: use unsafePerformSync
       task.run
            ^
res5: org.http4s.Response = Response(status=200, headers=Headers(Content-Type: text/plain; charset=UTF-8, Content-Length: 26))
```

As good functional programmers who like to delay our side effects, we
of course prefer to operate in [Task]s:

```scala
scala> val task = Ok(Task {
     |   println("I run when the Task is run.")
     |   "Mission accomplished!"
     | })
task: scalaz.concurrent.Task[org.http4s.Response] = scalaz.concurrent.Task@44bac25e

scala> task.run
<console>:24: warning: method run in class Task is deprecated: use unsafePerformSync
       task.run
            ^
I run when the Task is run.
res6: org.http4s.Response = Response(status=200, headers=Headers(Content-Type: text/plain; charset=UTF-8, Content-Length: 21))
```

Note that in both cases, a `Content-Length` header is calculated.
http4s waits for the `Future` or `Task` to complete before wrapping it
in its HTTP envelope, and thus has what it needs to calculate a
`Content-Length`.

#### Streaming bodies

Streaming bodies are supported by returning a `scalaz.stream.Process`.
Like `Future`s and `Task`s, the stream may be of any type that has an
`EntityEncoder`.

An intro to scalaz-stream is out of scope, but we can glimpse the
power here.  This stream emits the elapsed time every 100 milliseconds
for one second:

```scala
val drip = {
  import scala.concurrent.duration._
  implicit def defaultScheduler = scalaz.concurrent.Strategy.DefaultTimeoutScheduler
  scalaz.stream.time.awakeEvery(100.millis).map(_.toString).take(10)
}
// drip: scalaz.stream.Process[scalaz.concurrent.Task,String] = Append(Halt(End),Vector(<function1>))
```

We can see it for ourselves in the REPL:

```scala
scala> drip.to(scalaz.stream.io.stdOutLines).run.run
<console>:24: warning: method run in class Task is deprecated: use unsafePerformSync
       drip.to(scalaz.stream.io.stdOutLines).run.run
                                                 ^
```

When wrapped in a `Response`, http4s will flush each chunk of a
`Process` as they are emitted.  Note that a stream's length can't
generally be anticipated before it runs, so this triggers chunked
transfer encoding:

```scala
scala> Ok(drip).run
<console>:24: warning: method run in class Task is deprecated: use unsafePerformSync
       Ok(drip).run
                ^
res8: org.http4s.Response = Response(status=200, headers=Headers(Content-Type: text/plain; charset=UTF-8, Transfer-Encoding: chunked))
```

## Matching and extracting requests
