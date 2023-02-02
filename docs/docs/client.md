# HTTP Client

The `Client` trait in http4s can submit a `Request` to a server and return a `Response`.

```scala
trait Client[F[_]] {

  def run(req: Request[F]): Resource[F, Response[F]]

  //...
}
```

While `Client` is abstract in its effect type `F`, we will use concrete `IO` throughout this guide.

Let's briefly chat about the `Resource` wrapping the return type.
Every request/response pair is transmitted over a connection, which is a finite resource.
When you are done reading the `Response`, you return from the `Resource`.
This releases the connection so that it may be re-used by another request/response pair, or shutdown.

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

In order to play with a `Client` we'll first create an http4s `Server`.

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


@:callout(info)

Because this documentation is running in [mdoc] we need an implicit `IORuntime` to let us run our `IO` values explicitly with `.unsafeRunSync()`.
In real code you should construct your whole program in `IO` and assign it to `run` in `IOApp` as in the example above.


```scala mdoc:silent
import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = IORuntime.global
```

If you are following along in a REPL you will need to start the server in the background.
Additionally you will want a way to shutdown the server when you're done.

You can do this by starting the server like so:

```scala mdoc:silent
val shutdown = server.allocated.unsafeRunSync()._2
```

Later you can call `shutdown.unsafeRunSync()` to run the server's finalizers and release resources.

@:@


## Making Requests

### Creating the client

A good default choice is the `EmberClientBuilder`.
The `EmberClientBuilder` sets up a connection pool, enabling the reuse of connections for multiple requests, supports HTTP/1.x and HTTP/2, and is available for ScalaJS.

```scala mdoc:silent
import org.http4s.ember.client.EmberClientBuilder

EmberClientBuilder
  .default[IO]
  .build
  .use { client =>
    // use `client` here, returning an `IO`.
    client.expect[String]("http://localhost:8080/hello/Ember")
}
```

In the above example `.build` returns a `Resource[IO, Client]`.
We use the `Client` by passing `use` a function `Client => IO[B]`.
The result is a value that, when run, will acquire a `Client`, use it, and release it (even under cancellation or errors).

Note that we generally only call `.build.use` once per application and pass around the `Client`.
See the [Quick Start][quick-start] [g8 template][g8-template] for an example.

For the remainder of this tutorial, we'll use an alternate client backend
built on the standard `java.net` library client.  Unlike the ember
client, it does not need to be shut down.  Like the ember-client, and
any other http4s backend, it presents the exact same `Client`
interface!

It uses blocking I/O and is not suited for production, but it is
highly useful in a REPL or [mdoc] documentation:

```scala mdoc:silent
import org.http4s.client.JavaNetClientBuilder

// for REPL or mdoc use only!
val httpClient: Client[IO] = JavaNetClientBuilder[IO].create
```

### Describing a Request

To execute a GET request, we can call `expect` with the type we expect
and the URI we want:

```scala mdoc:silent
val helloEmber: IO[String] =
  httpClient.expect[String]("http://localhost:8080/hello/Ember")
```

We don't have any output from the server yet as we have not executed the request.
We have an `IO[String]` value, which is a description of a program that, when run,
will send a GET request to the server, and expect a plain text `String` response.

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

val inputs = List("Ember", "http4s", "Scala")

val getGreetings: IO[List[String]] =
  inputs.parTraverse(hello)
```

We use `parTraverse` to apply `hello` to each name and collect the results into
one `IO[List[String]]`.
The `par` prefix (as in "parallel") on `parTraverse` indicates that this
will happen concurrently not sequentially.

### Running a Request

We have built two programs: `helloEmber` will make a single request to get a greeting for Ember,
and `getGreetings` will make multiple concurrent requests getting multiple greetings.
In a production application we would likely compose these programs with other programs
up until we finally pass them to `run` in `IOApp` as seen in our intro example.

Here in [mdoc], or in a REPL, we manually run the `IO` with `unsafeRunSync()`.
Remember, you should not do this in your applications.

```scala mdoc
helloEmber.unsafeRunSync()

getGreetings.unsafeRunSync()
```


## Constructing a URI

Typically, to construct a `Request`, you use a `Uri` to represent the endpoint you
want to access.

There are a number of ways to construct a `Uri`.

If you have a literal string, you can use the `uri` string interpolator:

```scala mdoc
uri"https://my-awesome-service.com/foo/bar?wow=yeah"
```

This only works with literal strings because it uses a macro to validate the URI
format at compile-time.

Otherwise, you'll need to use `Uri.fromString(...)` and handle the case where
validation fails:

```scala mdoc:silent
val validUri = "https://my-awesome-service.com/foo/bar?wow=yeah"
val invalidUri = "yeah whatever"
```

```scala mdoc
val uri: ParseResult[Uri] = Uri.fromString(validUri)

val parseFailure: ParseResult[Uri] = Uri.fromString(invalidUri)
```

You can also build up a URI incrementally, e.g.:

```scala mdoc:silent
val baseUri: Uri = uri"http://foo.com"
val withPath: Uri = baseUri.withPath(path"/bar/baz")
val withQuery: Uri = withPath.withQueryParam("hello", "world")
```


## Middleware

Like the server [middleware], the client middleware is a wrapper around a
`Client` that provides a means of accessing or manipulating `Request`s
and `Response`s being sent.

Consider functions from `Int` to `String`. We could create a wrapper over functions of this type,
which would take an `Int => String` and return an `Int => String`.

Such a wrapper could make the result inspect its input, do something to it,
and call the original function with that input (or even another one).
Then it could look at the response and also make some actions based on it.

An example wrapper could look something like this:

```scala mdoc
def mid(f: Int => String): Int => String = in => {
  // `in` is the input originally passed to the function.
  // We can pass it to `f` directly.
  // Or use it to construct a new value.
  val resultOfF = f(in + 1)

  // `resultOfF` is the result of the function applied to the new input.
  // Similarly, we can return it directly, or build a new value.
  s"$in was incremented to yield $resultOfF"
}
```

If we wrap a simple function, say, one returning the String representation of a number:

```scala mdoc
val f1: Int => String = _.toString

// Here, we're applying our wrapper to `f1`. Notice that this is still a function.
val f2: Int => String = mid(f1)

f1(10)
f2(10)
```

We see how `f2` wraps `f1` by passing an incremented argument to the original function.
This wrapper could be considered a **middleware** over functions from `Int` to `String`.

Recall our simplified definition of `Client[F]` - it boils down to a single abstract method:

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

### Send a GET request

You can send a GET by calling the `expect` method on the client, passing a `Uri`:

```scala mdoc:silent
httpClient.expect[String](uri"https://google.com/")
```

If you need to do something more complicated like setting request headers, you
can build up a request object and pass that to `expect`:

```scala mdoc:silent
import cats.effect.IO
import org.http4s.Request
import org.http4s.Headers
import org.http4s.headers._
import org.http4s.MediaType

val request = Request[IO](
  method = Method.GET,
  uri = uri"https://my-lovely-api.com/",
  headers = Headers(
    Authorization(Credentials.Token(AuthScheme.Bearer, "open sesame")),
    Accept(MediaType.application.json),
  )
)

httpClient.expect[String](request)
```

### Send a POST request

You can send a POST request and decode the JSON response into a case class
by deriving an `EntityDecoder` for that case class:

```scala mdoc:silent
import cats.effect.IO
import org.http4s.circe._
import io.circe.generic.auto._

case class AuthResponse(access_token: String)

implicit val authResponseEntityDecoder: EntityDecoder[IO, AuthResponse] = jsonOf

val postRequest = Request[IO](
  method = Method.POST, 
  uri = uri"https://my-lovely-api.com/oauth2/token"
).withEntity(
  UrlForm(
    "grant_type" -> "client_credentials",
    "client_id" -> "my-awesome-client",
    "client_secret" -> "s3cr3t"
  )
)

httpClient.expect[AuthResponse](postRequest)
```

```scala mdoc:invisible
shutdown.unsafeRunSync()
```

## Calls to a JSON API

Take a look at [json].

## Body decoding / encoding

The reusable way to decode or encode a request is to write a custom `EntityDecoder`
or `EntityEncoder`. For that topic, take a look at [entity].

If you prefer a more fine-grained approach, some of the methods on `Client` take a
`Response[F] => F[A]` argument, such as `get`, which lets you add a function which
includes the decoding functionality, but ignores the media type.

```scala mdoc:silent
val endpoint = uri"http://localhost:8080/hello/Ember"
httpClient.get[Either[String, String]](endpoint) {
  case Status.Successful(r) => r.attemptAs[String].leftMap(_.message).value
  case r => r.as[String]
    .map(b => Left(s"Request failed with status ${r.status.code} and body $b"))
}
```

Your function has to consume the body before the returned `F` exits.
`Response.body` yields a `EntityBody` which is a type alias for `Stream[F, Byte]`.
It's this `Stream` that needs to be consumed within your effect `F`.

Do not do this:

```scala mdoc:silent
import org.http4s.EntityBody

// response.body is not consumed within `F`
httpClient.get[EntityBody[IO]]("some-url")(response => IO(response.body))
```

[service]: service.md
[entity]: entity.md
[json]: json.md
[middleware]: middleware.md
[Follow Redirect]: @API_URL@/org/http4s/client/middleware/FollowRedirect$
[Retry]: @API_URL@/org/http4s/client/middleware/Retry$
[Metrics]: @API_URL@/org/http4s/client/middleware/Metrics$
[Request Logger]: @API_URL@/org/http4s/client/middleware/RequestLogger$
[Response Logger]: @API_URL@/org/http4s/client/middleware/ResponseLogger$
[Logger]: @API_URL@/org/http4s/client/middleware/Logger$
[mdoc]: https://scalameta.org/mdoc/
[quick-start]: quickstart.md
[g8-template]: https://github.com/http4s/http4s.g8
