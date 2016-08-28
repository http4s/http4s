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
val helloWorldService = HttpService {
  case GET -> Root / "hello" / name =>
    Ok(s"Hello, $name.")
}
// helloWorldService: org.http4s.HttpService = Kleisli(org.http4s.package$HttpService$$$Lambda$6798/1391520341@30d54309)
```

#### Handling path parameters
Path params can be extracted and converted to a specific type but are
`String`s by default. There are numeric extractors provided in the form
of `IntVar` and `LongVar`.

```scala
import scalaz.concurrent.Task
// import scalaz.concurrent.Task

def getUserName(userId: Int): Task[String] = ???
// getUserName: (userId: Int)scalaz.concurrent.Task[String]

val usersService = HttpService {
  case request @ GET -> Root / "users" / IntVar(userId) =>
    Ok(getUserName(userId))
}
// usersService: org.http4s.HttpService = Kleisli(org.http4s.package$HttpService$$$Lambda$6798/1391520341@62c2a81f)
```

If you want to extract a variable of type `T`, you can provide a custom extractor
object which implements `def unapply(str: String): Option[T]`, similar to the way
in which `IntVar` does it.

```scala
import java.time.LocalDate
// import java.time.LocalDate

import scala.util.Try
// import scala.util.Try

import scalaz.concurrent.Task
// import scalaz.concurrent.Task

object LocalDateVar {
  def unapply(str: String): Option[LocalDate] = {
    if (!str.isEmpty)
      Try(LocalDate.parse(str)).toOption
    else
      None
  }
}
// defined object LocalDateVar

def getTemperatureForecast(date: LocalDate): Task[Double] = ???
// getTemperatureForecast: (date: java.time.LocalDate)scalaz.concurrent.Task[Double]

val dailyWeatherService = HttpService {
  case request @ GET -> Root / "weather" / "temperature" / LocalDateVar(localDate) =>
    Ok(getTemperatureForecast(localDate).map(s"The temperature on $localDate will be: " + _))
}
// dailyWeatherService: org.http4s.HttpService = Kleisli(org.http4s.package$HttpService$$$Lambda$6798/1391520341@23916aac)
```

#### Handling query parameters
A query parameter needs to have a `QueryParamDecoderMatcher` provided to
extract it. In order for the `QueryParamDecoderMatcher` to work there needs to
be an implicit `QueryParamDecoder[T]` in scope. `QueryParamDecoder`s for simple
types can be found in the `QueryParamDecoder` object. There are also
`QueryParamDecoderMatcher`s available which can be used to
return optional or validated parameter values.

In the example below we're finding query params named `country` and `year` and
then parsing them as a `String` and `java.time.Year`.

```scala
import java.time.Year
// import java.time.Year

import scalaz.ValidationNel
// import scalaz.ValidationNel

object CountryQueryParamMatcher extends QueryParamDecoderMatcher[String]("country")
// defined object CountryQueryParamMatcher

implicit val yearQueryParamDecoder = new QueryParamDecoder[Year] {
  def decode(queryParamValue: QueryParameterValue): ValidationNel[ParseFailure, Year] = {
    QueryParamDecoder.decodeBy[Year, Int](Year.of).decode(queryParamValue)
  }
}
// yearQueryParamDecoder: org.http4s.QueryParamDecoder[java.time.Year] = $anon$1@5ddc5fe4

object YearQueryParamMatcher extends QueryParamDecoderMatcher[Year]("year")
// defined object YearQueryParamMatcher

def getAverageTemperatureForCountryAndYear(country: String, year: Year): Task[Double] = ???
// getAverageTemperatureForCountryAndYear: (country: String, year: java.time.Year)scalaz.concurrent.Task[Double]

val averageTemperatureService = HttpService {
  case request @ GET -> Root / "weather" / "temperature" :? CountryQueryParamMatcher(country) +& YearQueryParamMatcher(year)  =>
    Ok(getAverageTemperatureForCountryAndYear(country, year).map(s"Average temperature for $country in $year was: " + _))
}
// averageTemperatureService: org.http4s.HttpService = Kleisli(org.http4s.package$HttpService$$$Lambda$6798/1391520341@7ed372d8)
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
 
```scala
import scalaz.concurrent.Task
// import scalaz.concurrent.Task

case class Tweet(id: Int, message: String)
// defined class Tweet

def tweetEncoder: EntityEncoder[Tweet] = ???
// tweetEncoder: org.http4s.EntityEncoder[Tweet]

implicit def tweetsEncoder: EntityEncoder[Seq[Tweet]] = ???
// tweetsEncoder: org.http4s.EntityEncoder[Seq[Tweet]]

def getTweet(tweetId: Int): Task[Tweet] = ???
// getTweet: (tweetId: Int)scalaz.concurrent.Task[Tweet]

def getPopularTweets(): Task[Seq[Tweet]] = ???
// getPopularTweets: ()scalaz.concurrent.Task[Seq[Tweet]]

val tweetService = HttpService {
  case request @ GET -> Root / "tweets" / "popular" =>
    Ok(getPopularTweets())
  case request @ GET -> Root / "tweets" / IntVar(tweetId) =>
    getTweet(tweetId).flatMap(Ok(_)(tweetEncoder))
}
// tweetService: org.http4s.HttpService = Kleisli(org.http4s.package$HttpService$$$Lambda$6798/1391520341@7376477f)
```

### Running your service

http4s supports multiple server backends.  In this example, we'll use
[blaze], the native backend supported by http4s.

We start from a `BlazeBuilder`, and then mount the `helloWorldService` under
the base path of `/` and the remainder of the services under the base
path of `/api`. The services can be mounted in any order as the request will be
matched against the longest base paths first. The `BlazeBuilder` is immutable
with chained methods, each returning a new builder.

Multiple `HttpService`s can be chained together with the `orElse` method by
importing `org.http4s.server.syntax._`. 

```scala
import org.http4s.server.blaze._
// import org.http4s.server.blaze._

import org.http4s.server.syntax._
// import org.http4s.server.syntax._

val services = usersService orElse dailyWeatherService orElse averageTemperatureService orElse tweetService
// services: org.http4s.Service[org.http4s.Request,org.http4s.Response] = Kleisli(<function1>)

val builder = BlazeBuilder.bindHttp(8080, "localhost").mountService(helloWorldService, "/").mountService(services, "/api")
// builder: org.http4s.server.blaze.BlazeBuilder = org.http4s.server.blaze.BlazeBuilder@5cf47d35
```

The `bindHttp` call isn't strictly necessary as the server will be set to run
using defaults of port 8080 and the loopback address. The `mountService` call
associates a base path with a `HttpService`.

A builder can be `run` to start the server.

```scala
val server = builder.run
// server: org.http4s.server.Server = BlazeServer(/127.0.0.1:8080)
```

### Running your service as an `App`

To run a server as an `App` simply extend
`org.http4s.server.ServerApp` and implement the `server` method. The server
will be shutdown automatically when the program exits via a shutdown hook,
so you don't need to clean up any resources explicitly.

```scala
import org.http4s.server.{Server, ServerApp}
// import org.http4s.server.{Server, ServerApp}

import org.http4s.server.blaze._
// import org.http4s.server.blaze._

object Main extends ServerApp {
  override def server(args: List[String]): Task[Server] = {
    BlazeBuilder
      .bindHttp(8080, "localhost")
      .mountService(services, "/api")
      .start
  }
}
// defined object Main
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
// client: org.http4s.client.Client = Client(Kleisli(org.http4s.client.blaze.BlazeClient$$$Lambda$6852/1710699587@b9df5cd),scalaz.concurrent.Task@55d61768)
```

### Describing a call

To execute a GET request, we can call `getAs` with the type we expect
and the URI we want:

```scala
val helloJames = client.expect[String]("http://localhost:8080/hello/James")
// helloJames: scalaz.concurrent.Task[String] = scalaz.concurrent.Task@19bb8fc3
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
// greetingList: scalaz.concurrent.Task[List[String]] = scalaz.concurrent.Task@3cc2c567
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
// <console>:49: warning: method run in class Task is deprecated: use unsafePerformSync
//        greetingList.run.mkString("\n")
//                     ^
// res0: String =
// Hello, Christopher.
// Hello, Ashley.
// Hello, Jessica.
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
