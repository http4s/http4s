
# HTTP Client

How do we know the server is running?  Let's create a client with
http4s to try our service.

A recap of the dependencies for this example, in case you skipped the [service] example. Ensure you have the following dependencies in your build.sbt:

```scala
scalaVersion := "2.13.4" // Also supports 2.11.x and 2.12.x

val http4sVersion = "@{version.http4s.doc}"

// Only necessary for SNAPSHOT releases
resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-blaze-server" % http4sVersion,
  "org.http4s" %% "http4s-blaze-client" % http4sVersion
)
```

Then we create the [service] again so [mdoc] picks it up:
>
```scala mdoc:silent
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.blaze.server._
```

Blaze needs a [[`ConcurrentEffect`]] instance, which is derived from
[[`ContextShift`]].  The following lines are not necessary if you are
in an [[`IOApp`]]:

```scala mdoc:silent:nest
import scala.concurrent.ExecutionContext.global
implicit val cs: ContextShift[IO] = IO.contextShift(global)
implicit val timer: Timer[IO] = IO.timer(global)
```

Finish setting up our server:

```scala mdoc:nest
import scala.concurrent.ExecutionContext.global

val app = HttpRoutes.of[IO] {
  case GET -> Root / "hello" / name =>
    Ok(s"Hello, $name.")
}.orNotFound

val server = BlazeServerBuilder[IO](global)
  .bindHttp(8080, "localhost")
  .withHttpApp(app)
  .resource
```

We'll start the server in the background.  The `IO.never` keeps it
running until we cancel the fiber.

```scala mdoc:nest
val fiber = server.use(_ => IO.never).start.unsafeRunSync()
```


### Creating the client

A good default choice is the `BlazeClientBuilder`.  The
`BlazeClientBuilder` maintains a connection pool and speaks HTTP 1.x.

```scala mdoc
import org.http4s.blaze.client._
import org.http4s.client._
import scala.concurrent.ExecutionContext.global
```

```scala mdoc:silent
BlazeClientBuilder[IO](global).resource.use { client =>
  // use `client` here and return an `IO`.
  // the client will be acquired and shut down
  // automatically each time the `IO` is run.
  IO.unit
}
```

For the remainder of this tutorial, we'll use an alternate client backend
built on the standard `java.net` library client.  Unlike the blaze
client, it does not need to be shut down.  Like the blaze-client, and
any other http4s backend, it presents the exact same `Client`
interface!

It uses blocking IO and is less suited for production, but it is
highly useful in a REPL:

```scala mdoc:silent:nest
import cats.effect.Blocker
import java.util.concurrent._

val blockingPool = Executors.newFixedThreadPool(5)
val blocker = Blocker.liftExecutorService(blockingPool)
val httpClient: Client[IO] = JavaNetClientBuilder[IO](blocker).create
```

### Describing a call

To execute a GET request, we can call `expect` with the type we expect
and the URI we want:

```scala mdoc
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

```scala mdoc:silent
import cats._, cats.effect._, cats.implicits._
import org.http4s.Uri
```

```scala mdoc
def hello(name: String): IO[String] = {
  val target = uri"http://localhost:8080/hello/" / name
  httpClient.expect[String](target)
}

val people = Vector("Michael", "Jessica", "Ashley", "Christopher")

val greetingList = people.parTraverse(hello)
```

Observe how simply we could combine a single `F[String]` returned
by `hello` into a scatter-gather to return a `F[List[String]]`.

## Making the call

It is best to run your `F` "at the end of the world."  The "end of
the world" varies by context:

* In a command line app, it's your main method.
* In an `HttpApp[F]`, an `F[Response[F]]` is returned to be run by the
  server.
* Here in the REPL, the last line is the end of the world.  Here we go:

```scala mdoc:nest
val greetingsStringEffect = greetingList.map(_.mkString("\n"))
greetingsStringEffect.unsafeRunSync()
```

## Constructing a URI

Before you can make a call, you'll need a `Uri` to represent the endpoint you
want to access.

There are a number of ways to construct a `Uri`.

If you have a literal string, you can use `uri"..."`:

```scala mdoc:nest
uri"https://my-awesome-service.com/foo/bar?wow=yeah"
```

This only works with literal strings because it uses a macro to validate the URI
format at compile-time.

Otherwise, you'll need to use `Uri.fromString(...)` and handle the case where
validation fails:

```scala mdoc:nest
val validUri = "https://my-awesome-service.com/foo/bar?wow=yeah"
val invalidUri = "yeah whatever"

val uri: Either[ParseFailure, Uri] = Uri.fromString(validUri)

val parseFailure: Either[ParseFailure, Uri] = Uri.fromString(invalidUri)
```

You can also build up a URI incrementally, e.g.:

```scala mdoc:nest
val baseUri: Uri = uri"http://foo.com"
val withPath: Uri = baseUri.withPath(path"/bar/baz")
val withQuery: Uri = withPath.withQueryParam("hello", "world")
```

## Middleware

Like the server [middleware], the client middleware is a wrapper around a
`Client` that provides a means of accessing or manipulating `Request`s
and `Response`s being sent.

Consider functions from `Int` to `String. We could create a wrapper over functions of this type,
which would take an `Int => String` and return an `Int => String`.

Such a wrapper could make the result inspect its input, do something to it,
and call the original function with that input (or even another one).
Then it could look at the response and also make some actions based on it.

An example wrapper could look something like this:

```scala mdoc
def mid(f: Int => String): Int => String = in => {
  // here, `in` is the input originally passed to the function
  // we can decide to pass it to `f`, or modify it first. We'll change it for the example.
  val resultOfF = f(in + 1)

  // Now, `resultOfF` is the result of the function applied with the modified result.
  // We can return it verbatim or _also_ modify it first! We could even ignore it.
  // Here, we'll use both results - the one we got from the original call (f(in)) and the customized one (f(in + 1)).
  s"${f(in)} is the original result, but $resultOfF's input was modified!"
}
```

If we were to wrap a simple function, say, one returning the String representation of a number:

```scala mdoc
val f1: Int => String = _.toString

// Here, we're applying our wrapper to `f1`. Notice that this is still a function.
val f2: Int => String = mid(f1)

f1(10)
f2(10)
```

We would see how it's changing the result of the `f1` function by giving it another input.

This wrapper could be considered a **middleware** over functions from `Int` to `String`.
Now consider a simplified definition of `Client[F]` - it boils down to a single abstract method:

```scala
trait Client[F[_]] {
  def run(request: Request[F]): Resource[F, Response[F]]
}
```

Knowing this, we could say a `Client[F]` is equivalent to a function from `Request[F]` to `Resource[F, Response[F]]`. In fact, given a client, we could call `client.run _` to get that function.

A client middleware follows the same idea as our original middleware did: it takes a `Client` (which is a function) and returns another `Client` (which is also a function).

It can see the input `Request[F]` that we pass to the client when we call it, it can modify that request, pass it to the underlying client (or any other client, really!), and do all sorts of other things, including effects - all it has to do is return a `Resource[F, Response[F]]`.

The real definition of `Client` is a little more complicated because there's several more abstract methods.
If you want to implement a client using just a function (for example, to make a middleware), consider using `Client.apply`.

A simple middleware, which would add a constant header to every request and response, could look like this:

```scala mdoc
import org.typelevel.ci.CIString

def addTestHeader[F[_]: Concurrent](underlying: Client[F]): Client[F] = Client[F] { req =>
  underlying
    .run(
      req.withHeaders(Header.Raw(CIString("X-Test-Request"), "test"))
    )
    .map(
      _.withHeaders(Header.Raw(CIString("X-Test-Response"), "test"))
    )
}
```

As the caller of the client you would get from this, you would see the extra header in the response.
Similarly, every service called by the client would see an extra header in the requests.

### Included Middleware

Http4s includes some middleware Out of the Box in the `org.http4s.client.middleware`
package. These include:

* Following of redirect responses ([Follow Redirect])
* Retrying of requests ([Retry])
* Metrics gathering ([Metrics])
* Logging of requests ([Request Logger])
* Logging of responses ([Response Logger])
* Logging of requests and responses ([Logger])

### Metrics Middleware

Apart from the middleware mentioned in the previous section. There is, as well,
Out of the Box middleware for Dropwizard and Prometheus metrics

#### Dropwizard Metrics Middleware

To make use of this metrics middleware the following dependencies are needed:

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-client" % http4sVersion,
  "org.http4s" %% "http4s-dropwizard-metrics" % http4sVersion
)
```

We can create a middleware that registers metrics prefixed with a
provided prefix like this.

```scala mdoc:silent
import org.http4s.client.middleware.Metrics
import org.http4s.metrics.dropwizard.Dropwizard
import com.codahale.metrics.SharedMetricRegistries
```
```scala mdoc:nest
implicit val clock = Clock.create[IO]
val registry = SharedMetricRegistries.getOrCreate("default")
val requestMethodClassifier = (r: Request[IO]) => Some(r.method.toString.toLowerCase)

val meteredClient = 
  Metrics[IO](Dropwizard(registry, "prefix"), requestMethodClassifier)(httpClient)
```

A `classifier` is just a function `Request[F] => Option[String]` that allows
to add a subprefix to every metric based on the `Request`

#### Prometheus Metrics Middleware

To make use of this metrics middleware the following dependencies are needed:

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-client" % http4sVersion,
  "org.http4s" %% "http4s-prometheus-metrics" % http4sVersion
)
```

We can create a middleware that registers metrics prefixed with a
provided prefix like this.

```scala mdoc:silent
import cats.effect.{Resource, IO}
import org.http4s.client.middleware.Metrics
import org.http4s.metrics.prometheus.Prometheus
```
```scala mdoc:nest
implicit val clock = Clock.create[IO]
val requestMethodClassifier = (r: Request[IO]) => Some(r.method.toString.toLowerCase)

val meteredClient: Resource[IO, Client[IO]] =
  for {
    registry <- Prometheus.collectorRegistry[IO]
    metrics <- Prometheus.metricsOps[IO](registry, "prefix")
  } yield Metrics[IO](metrics, requestMethodClassifier)(httpClient)
```


A `classifier` is just a function `Request[F] => Option[String]` that allows
to add a label to every metric based on the `Request`

## Examples

### Send a GET request, treating the response as a string

You can send a GET by calling the `expect` method on the client, passing a `Uri`:

```scala mdoc:nest
httpClient.expect[String](uri"https://google.com/")
```

If you need to do something more complicated like setting request headers, you
can build up a request object and pass that to `expect`:

```scala mdoc:silent
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.MediaType
import org.http4s.Method._
```

```scala mdoc:nest
val request = GET(
  uri"https://my-lovely-api.com/",
  Authorization(Credentials.Token(AuthScheme.Bearer, "open sesame")),
  Accept(MediaType.application.json)
)

httpClient.expect[String](request)
```

### Post a form, decoding the JSON response to a case class

```scala mdoc:nest
import org.http4s.circe._
import io.circe.generic.auto._

case class AuthResponse(access_token: String)

implicit val authResponseEntityDecoder: EntityDecoder[IO, AuthResponse] =
  jsonOf[IO, AuthResponse]

val postRequest = POST(
  UrlForm(
    "grant_type" -> "client_credentials",
    "client_id" -> "my-awesome-client",
    "client_secret" -> "s3cr3t"
  ),
  uri"https://my-lovely-api.com/oauth2/token"
)

httpClient.expect[AuthResponse](postRequest)
```

```scala mdoc:nest:invisible
fiber.cancel.unsafeRunSync()
```

## Calls to a JSON API

Take a look at [json].

## Body decoding / encoding

The reusable way to decode/encode a request is to write a custom `EntityDecoder`
and `EntityEncoder`. For that topic, take a look at [entity].

If you prefer a more fine-grained approach, some of the methods take a `Response[F]
=> F[A]` argument, such as `run` or `get`, which lets you add a function which includes the
decoding functionality, but ignores the media type.

```scala
client.run(req).use {
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

```scala mdoc:nest:invisible
blockingPool.shutdown()
```

[service]: ../service
[entity]: ../entity
[json]: ../json
[`ContextShift`]: https://typelevel.org/cats-effect/datatypes/contextshift.html
[`ConcurrentEffect`]: https://typelevel.org/cats-effect/typeclasses/concurrent-effect.html
[`IOApp`]: https://typelevel.org/cats-effect/datatypes/ioapp.html
[middleware]: ../middleware
[Follow Redirect]: ../api/org/http4s/client/middleware/FollowRedirect$
[Retry]: ../api/org/http4s/client/middleware/Retry$
[Metrics]: ../api/org/http4s/client/middleware/Metrics$
[Request Logger]: ../api/org/http4s/client/middleware/RequestLogger$
[Response Logger]: ../api/org/http4s/client/middleware/ResponseLogger$
[Logger]: ../api/org/http4s/client/middleware/Logger$
[mdoc]: https://scalameta.org/mdoc/
