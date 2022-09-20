# HTTP Client

The `Client` trait in http4s can submit `Request`s to a server and return a `Response`.

```scala
trait Client[F[_]] {

  def run(req: Request[F]): Resource[F, Response[F]]

  //...
}
```

Here's a quick example app to print the response of a GET request.

```scala mdoc
import cats.effect.{IO, IOApp}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.client.Client

object Hello extends IOApp.Simple {

  def printHello(client: Client[IO]): IO[Unit] =
    client
      .expect[String]("http://localhost:8080/hello/Ember")
      .flatMap(IO.println)

  val run: IO[Unit] = EmberClientBuilder
    .default[IO]
    .build
    .use(client => printHello(client))

}
```


## Setup

In order to play with a `Client` we'll first create a http4s `Server`.

Ensure you have the following dependencies in your build.sbt:

```scala
scalaVersion := "2.13.8" // Also supports 2.12.x and 3.x

val http4sVersion = "@VERSION@"

libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl"          % http4sVersion,
)
```

Because this documentation is running in [mdoc] we need an implicit `IORuntime`.
This would be taken care of for you if you were using `IOApp` as seen above.

```scala mdoc:silent
import cats.effect.unsafe.IORuntime

implicit val runtime: IORuntime = IORuntime.global
```

Now we can finish setting up our server:

```scala mdoc:silent
import com.comcast.ip4s._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.Logger

val app = HttpRoutes.of[IO] {
  case GET -> Root / "hello" / name =>
    Ok(s"Hello, $name.")
}.orNotFound

val finalHttpApp = Logger.httpApp(true, true)(app)

val server = EmberServerBuilder
  .default[IO]
  .withHost(ipv4"0.0.0.0")
  .withPort(port"8080")
  .withHttpApp(finalHttpApp)
  .build
```

We'll start the server in the background.

```scala mdoc:silent
val shutdown = server.allocated.unsafeRunSync()._2
```

## Making Requests

### Creating the client

A good default choice is the `EmberClientBuilder`.  The
`EmberClientBuilder` maintains a connection pool and speaks HTTP 1.x.

```scala mdoc:silent
import org.http4s.ember.client.EmberClientBuilder

EmberClientBuilder.default[IO].build.use { client =>
  // use `client` here and return an `IO`.
  // the client will be acquired and shut down
  // automatically each time the `IO` is run.

  // we'll explain this shortly
  client.expect[String]("http://localhost:8080/hello/Ember")
}
```

For the remainder of this tutorial, we'll use an alternate client backend
built on the standard `java.net` library client.  Unlike the ember
client, it does not need to be shut down.  Like the ember-client, and
any other http4s backend, it presents the exact same `Client`
interface!

It uses blocking IO and is less suited for production, but it is
highly useful in [mdoc] documentation:

```scala mdoc:silent
import org.http4s.client.JavaNetClientBuilder

// for our mdoc use only!
val httpClient: Client[IO] = JavaNetClientBuilder[IO].create
```

### Describing a Request

To execute a GET request, we can call `expect` with the type we expect
and the URI we want:

```scala mdoc:silent
val helloJames: IO[String] =
  httpClient.expect[String]("http://localhost:8080/hello/James")
```

We don't have any output from the server yet as we have not executed the
request.  We have an `IO[String]` value, which is a description of a
program that will request a `String` from the server when run.

Let's build another program that makes requests in parallel to greet a
collection of people:

```scala mdoc:silent
import cats.effect.IO
import cats.syntax.all._
import org.http4s.Uri

def hello(name: String): IO[String] = {
  val target = uri"http://localhost:8080/hello/" / name
  httpClient.expect[String](target)
}

val people = List("Michael", "Jessica", "Ashley", "Christopher")

val greetingList: IO[List[String]] =
  people.parTraverse(hello)
```

We use `parTraverse` to apply `hello` to each person and collect the results into
one `IO[List[String]]`. The `par` prefix on `parTraverse` indicates that this
will happen for each person concurrently.

### Running a Request

So far we have built two programs, `helloJames` will make a request to greet James,
and `greetingList` will make multiple concurrent requests to greet multiple people.
In a production application we would likely compose these programs with other programs
up until we finally pass them to `run` in `IOApp` as seen in our intro example.

Here in [mdoc], or in a scala REPL, we manually run the `IO` with `unsafeRunSync()`.
Remember, you should not do this in your applications.

```scala mdoc
helloJames.unsafeRunSync()

greetingList.map(_.mkString("\n")).unsafeRunSync()
```


## Constructing a URI

Before you can make a call, you'll need a `Uri` to represent the endpoint you
want to access.

There are a number of ways to construct a `Uri`.

If you have a literal string, you can use `uri"..."`:

```scala mdoc
uri"https://my-awesome-service.com/foo/bar?wow=yeah"
```

This only works with literal strings because it uses a macro to validate the URI
format at compile-time.

Otherwise, you'll need to use `Uri.fromString(...)` and handle the case where
validation fails:

```scala mdoc
val validUri = "https://my-awesome-service.com/foo/bar?wow=yeah"
val invalidUri = "yeah whatever"

val uri: Either[ParseFailure, Uri] = Uri.fromString(validUri)

val parseFailure: Either[ParseFailure, Uri] = Uri.fromString(invalidUri)
```

You can also build up a URI incrementally, e.g.:

```scala mdoc
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
import cats.effect.MonadCancelThrow
import org.typelevel.ci._

def addTestHeader[F[_]: MonadCancelThrow](underlying: Client[F]): Client[F] = Client[F] { req =>
  underlying
    .run(
      req.withHeaders(Header.Raw(ci"X-Test-Request", "test"))
    )
    .map(
      _.withHeaders(Header.Raw(ci"X-Test-Response", "test"))
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
Out of the Box middleware for [Dropwizard](https://http4s.github.io/http4s-dropwizard-metrics/) and [Prometheus](https://http4s.github.io/http4s-prometheus-metrics/) metrics.

## Examples

### Send a GET request, treating the response as a string

You can send a GET by calling the `expect` method on the client, passing a `Uri`:

```scala mdoc:silent
httpClient.expect[String](uri"https://google.com/")
```

If you need to do something more complicated like setting request headers, you
can build up a request object and pass that to `expect`:

```scala mdoc:silent
import org.http4s.client.dsl.io._
import org.http4s.headers._
import org.http4s.MediaType
```

```scala mdoc
val request = GET(
  uri"https://my-lovely-api.com/",
  Authorization(Credentials.Token(AuthScheme.Bearer, "open sesame")),
  Accept(MediaType.application.json)
)

httpClient.expect[String](request)
```

### Post a form, decoding the JSON response to a case class

```scala mdoc
import org.http4s.circe._
import io.circe.generic.auto._

case class AuthResponse(access_token: String)

implicit val authResponseEntityDecoder: EntityDecoder[IO, AuthResponse] = jsonOf

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

```scala mdoc:invisible
shutdown.unsafeRunSync()
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

[service]: service.md
[entity]: entity.md
[json]: json.md
[`IOApp`]: https://typelevel.org/cats-effect/datatypes/ioapp.html
[middleware]: middleware.md
[Follow Redirect]: @API_URL@/org/http4s/client/middleware/FollowRedirect$
[Retry]: @API_URL@/org/http4s/client/middleware/Retry$
[Metrics]: @API_URL@/org/http4s/client/middleware/Metrics$
[Request Logger]: @API_URL@/org/http4s/client/middleware/RequestLogger$
[Response Logger]: @API_URL@/org/http4s/client/middleware/ResponseLogger$
[Logger]: @API_URL@/org/http4s/client/middleware/Logger$
[mdoc]: https://scalameta.org/mdoc/
