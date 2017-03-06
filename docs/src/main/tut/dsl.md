---
menu: tut
weight: 110
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

```tut:book
import org.http4s._, org.http4s.dsl._
import fs2.Task
```

The central concept of http4s-dsl is pattern matching.  An
`HttpService` is declared as a simple series of case statements.  Each
case statement attempts to match and optionally extract from an
incoming `Request`.  The code associated with the first matching case
is used to generate a `Task[Response]`.

The simplest case statement matches all requests without extracting
anything.  The right hand side of the request must return a
`Task[Response]`.

```tut:book
val service = HttpService {
  case _ =>
    Task.delay(Response(Status.Ok))
}
```

## Testing the Service

One beautiful thing about the `HttpService` model is that we don't
need a server to test our route.  We can construct our own request
and experiment directly in the REPL.

```tut
val getRoot = Request(Method.GET, uri("/"))

val task = service.run(getRoot)
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

```tut
val response = task.unsafeRun
```

Cool.

## Generating responses

We'll circle back to more sophisticated pattern matching of requests,
but it will be a tedious affair until we learn a more succinct way of
generating `Task[Response]`s.

### Status codes

http4s-dsl provides a shortcut to create a `Task[Response]` by
applying a status code:

```tut
val okTask = Ok()
val ok = okTask.unsafeRun
```

This simple `Ok()` expression succinctly says what we mean in a
service:

```tut:book
HttpService {
  case _ => Ok()
}.run(getRoot).unsafeRun
```

This syntax works for other status codes as well.  In our example, we
don't return a body, so a `204 No Content` would be a more appropriate
response:

```tut:book
HttpService {
  case _ => NoContent()
}.run(getRoot).unsafeRun
```

### Headers

http4s adds a minimum set of headers depending on the response, e.g:

```tut
Ok("Ok response.").unsafeRun.headers
```

Extra headers can be added using `putHeaders`, for example to specify cache policies:

```tut:book
import org.http4s.headers.`Cache-Control`
import org.http4s.CacheDirective.`no-cache`
import cats.data.NonEmptyList
import org.http4s.util.nonEmptyList
```

```tut
Ok("Ok response.").putHeaders(`Cache-Control`(NonEmptyList(`no-cache`(), Nil))).unsafeRun.headers
```

http4s defines all the well known headers directly, but sometimes you need to
define custom headers, typically prefixed by an `X-`. In simple cases you can
construct a `Header` instance by hand

```tut
Ok("Ok response.").putHeaders(Header("X-Auth-Token", "value")).unsafeRun.headers
```

### Cookies

http4s has special support for Cookie headers using the `Cookie` type to add
and invalidate cookies. Adding a cookie will generate the correct `Set-Cookie` header:

```tut
Ok("Ok response.").addCookie(Cookie("foo", "bar")).unsafeRun.headers
```

`Cookie` can be further customized to set, e.g., expiration, the secure flag, httpOnly, flag, etc

```tut
import java.time.Instant

Ok("Ok response.").addCookie(Cookie("foo", "bar", expires = Some(Instant.now), httpOnly = true, secure = true)).unsafeRun.headers
```

To request a cookie to be removed on the client, you need to set the cookie value
to empty. http4s can do that with `removeCookie`

```tut
Ok("Ok response.").removeCookie("foo").unsafeRun.headers
```

### Responding with a body

#### Simple bodies

Most status codes take an argument as a body.  In http4s, `Request`
and `Response` bodies are represented as a
`fs2.Stream[Task, ByteVector]`.  It's also considered good
HTTP manners to provide a `Content-Type` and, where known in advance,
`Content-Length` header in one's responses.

All of this hassle is neatly handled by http4s' [EntityEncoder]s.
We'll cover these in more depth in another tut.  The important point
for now is that a response body can be generated for any type with an
implicit `EntityEncoder` in scope.  http4s provides several out of the
box:

```tut
Ok("Received request.").unsafeRun

import java.nio.charset.StandardCharsets.UTF_8
Ok("binary".getBytes(UTF_8)).unsafeRun
```

Per the HTTP specification, some status codes don't support a body.
http4s prevents such nonsense at compile time:

```tut:fail
NoContent("does not compile")
```

#### Asynchronous responses

While http4s prefers `Task`, you may be working with libraries that
use standard library [Future]s.  Some relevant imports:

```tut:book
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
```

You can seamlessly respond with a `Future` of any type that has an
`EntityEncoder`.

```tut
val task = Ok(Future {
  println("I run when the future is constructed.")
  "Greetings from the future!"
})
task.unsafeRun
```

As good functional programmers who like to delay our side effects, we
of course prefer to operate in [Task]s:

```tut
implicit val strategy = fs2.Strategy.fromFixedDaemonPool(2, threadName = "strategy")
val task = Ok(Task {
  println("I run when the Task is run.")
  "Mission accomplished!"
})
task.unsafeRun
```

Note that in both cases, a `Content-Length` header is calculated.
http4s waits for the `Future` or `Task` to complete before wrapping it
in its HTTP envelope, and thus has what it needs to calculate a
`Content-Length`.

#### Streaming bodies

Streaming bodies are supported by returning a `fs2.Stream`.
Like `Future`s and `Task`s, the stream may be of any type that has an
`EntityEncoder`.

An intro to `Stream` is out of scope, but we can glimpse the
power here.  This stream emits the elapsed time every 100 milliseconds
for one second:

```tut:book
implicit val scheduler = fs2.Scheduler.fromFixedDaemonPool(2, threadName = "scheduler")
val drip = {
  import scala.concurrent.duration._
  fs2.time.awakeEvery[Task](100.millis).map(_.toString).take(10)
}
```

We can see it for ourselves in the REPL:

```tut
val dripOutTask = drip.through(fs2.text.lines).through(_.evalMap(s => {Task.delay{println(s); s}})).run
dripOutTask.unsafeRun
```

When wrapped in a `Response`, http4s will flush each chunk of a
`Stream` as they are emitted.  Note that a stream's length can't
generally be anticipated before it runs, so this triggers chunked
transfer encoding:

```tut
Ok(drip).unsafeRun
```

## Matching and extracting requests

A `Request` is a regular `case class` - you can destructure it to extract its
values. By extension, you can also `match/case` it with different possible
destructurings. To build these different extractors, you can make use of the
DSL.

Most often, you extract the `Request` into a HTTP `Method` (verb) and the path,
via the `->` object. On the left side, you'll have the HTTP `Method`, on the
other side the path. Naturally, `_` is a valid matcher too, so any call to
`/api` can be blocked, regardless of `Method`:

```tut
HttpService {
  case request @ _ -> Root / "api" => Forbidden()
}
```

To also block all subcalls `/api/...`, you'll need `/:`, which is right
associative, and matches everything after, and not just the next element:

```tut
HttpService {
  case request @ _ -> "api" /: _ => Forbidden()
}
```

For matching more than one `Method`, there's `|`:

```tut
HttpService {
  case request @ (GET | POST) -> Root / "api"  => ???
}
```

Honorable mention: `~`, for matching file extensions.

```tut
HttpService {
  case GET -> Root / file ~ "json" => Ok(s"""{"response": "You asked for $file"}""")
}
```

### Handling path parameters
Path params can be extracted and converted to a specific type but are
`String`s by default. There are numeric extractors provided in the form
of `IntVar` and `LongVar`.

```tut:book
import fs2.Task

def getUserName(userId: Int): Task[String] = ???

val usersService = HttpService {
  case request @ GET -> Root / "users" / IntVar(userId) =>
    Ok(getUserName(userId))
}
```

If you want to extract a variable of type `T`, you can provide a custom extractor
object which implements `def unapply(str: String): Option[T]`, similar to the way
in which `IntVar` does it.

```tut:book
import java.time.LocalDate
import scala.util.Try
import fs2.Task
import org.http4s.client._

object LocalDateVar {
  def unapply(str: String): Option[LocalDate] = {
    if (!str.isEmpty)
      Try(LocalDate.parse(str)).toOption
    else
      None
  }
}

def getTemperatureForecast(date: LocalDate): Task[Double] = Task(42.23)

val dailyWeatherService = HttpService {
  case request @ GET -> Root / "weather" / "temperature" / LocalDateVar(localDate) =>
    Ok(getTemperatureForecast(localDate).map(s"The temperature on $localDate will be: " + _))
}

println(GET(Uri.uri("/weather/temperature/2016-11-05")).flatMap(dailyWeatherService(_)).unsafeRun)
```

### Handling query parameters
A query parameter needs to have a `QueryParamDecoderMatcher` provided to
extract it. In order for the `QueryParamDecoderMatcher` to work there needs to
be an implicit `QueryParamDecoder[T]` in scope. `QueryParamDecoder`s for simple
types can be found in the `QueryParamDecoder` object. There are also
`QueryParamDecoderMatcher`s available which can be used to
return optional or validated parameter values.

In the example below we're finding query params named `country` and `year` and
then parsing them as a `String` and `java.time.Year`.

```tut:book
import java.time.Year
import cats.data.ValidatedNel

object CountryQueryParamMatcher extends QueryParamDecoderMatcher[String]("country")

implicit val yearQueryParamDecoder = new QueryParamDecoder[Year] {
  def decode(queryParamValue: QueryParameterValue): ValidatedNel[ParseFailure, Year] = {
    QueryParamDecoder.decodeBy[Year, Int](Year.of).decode(queryParamValue)
  }
}

object YearQueryParamMatcher extends QueryParamDecoderMatcher[Year]("year")

def getAverageTemperatureForCountryAndYear(country: String, year: Year): Task[Double] = ???

val averageTemperatureService = HttpService {
  case request @ GET -> Root / "weather" / "temperature" :? CountryQueryParamMatcher(country) +& YearQueryParamMatcher(year)  =>
    Ok(getAverageTemperatureForCountryAndYear(country, year).map(s"Average temperature for $country in $year was: " + _))
}
```

[EntityEncoder]: ../api/#org.http4s.EntityEncoder$
