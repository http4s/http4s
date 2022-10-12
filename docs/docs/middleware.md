# Middleware

A middleware is an abstraction around a [service] that provides a means of manipulating
the `Request` sent to service, and/or the `Response` returned by the service. In
some cases, such as [Authentication], middleware may even prevent the service
from being called.

At its most basic, middleware is a function that takes one service
and returns another. The middleware function can take any additional parameters 
it needs to perform its task. Let's look at a simple example.

For this, we'll need a dependency on the http4s [dsl].

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion
)
```
and some imports.

```scala mdoc:silent
import cats.data.Kleisli
import cats.effect._
import cats.syntax.all._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
```


If you're in a REPL, we also need a runtime:

```scala mdoc:silent
import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
```

Then, we can create a middleware that adds a header to successful responses from
the underlying `HttpRoutes` like this.

```scala mdoc
def myMiddle(service: HttpRoutes[IO], header: Header.ToRaw): HttpRoutes[IO] = Kleisli { (req: Request[IO]) =>
  service(req).map {
    case Status.Successful(resp) =>
      resp.putHeaders(header)
    case resp => resp
  }
}
```

All we do here is pass the request to the service,
which returns an `F[Response]`. So, we use `map` to get the request out of the task,
add the header if the response is a success, and then pass the response on. We could
just as easily modify the request before we passed it to the service.

Now, let's create a simple service. As mentioned between [service] and [dsl], because `HttpRoutes`
is implemented as a [`Kleisli`], which is just a function at heart, we can test a
service without a server. Due to an `HttpRoutes[F]` returns a `F[Response[F]]`,
we need to call `unsafeRunSync` on the result of the function to extract the `Response[F]`.
Note that basically, you shouldn't use `unsafeRunSync` in your application. 
Here we use it for demo reasons only.

```scala mdoc:silent
val service = HttpRoutes.of[IO] {
  case GET -> Root / "bad" =>
    BadRequest()
  case _ => Ok()
}

val goodRequest = Request[IO](Method.GET, uri"/")
val badRequest = Request[IO](Method.GET, uri"/bad")
```

```scala mdoc
service.orNotFound(goodRequest).unsafeRunSync()
service.orNotFound(badRequest).unsafeRunSync()
```

Now, we'll apply the service to our middleware function to create a new service, and try it out.

```scala mdoc:silent
val modifiedService = myMiddle(service, "SomeKey" -> "SomeValue");
```

```scala mdoc
modifiedService.orNotFound(goodRequest).unsafeRunSync()
modifiedService.orNotFound(badRequest).unsafeRunSync()
```

Note that the successful response has your header added to it.

If you intend to use you middleware in multiple places, you may want to implement
it as an `object` and use the `apply` method.

```scala mdoc:silent
object MyMiddle {
  def addHeader(resp: Response[IO], header: Header.ToRaw) =
    resp match {
      case Status.Successful(resp) => resp.putHeaders(header)
      case resp => resp
    }

  def apply(service: HttpRoutes[IO], header: Header.ToRaw) =
    service.map(addHeader(_, header))
}

val newService = MyMiddle(service, "SomeKey" -> "SomeValue")
```

```scala mdoc
newService.orNotFound(goodRequest).unsafeRunSync()
newService.orNotFound(badRequest).unsafeRunSync()
```

Let's consider Authentication middleware as an example. Authentication
middleware is a function that takes `AuthedRoutes[F]` 
(that translates to `AuthedRequest[F, T] => F[Option[Response[F]]]`) 
and returns `HttpRoutes[F]` (that translates to `Request[F] => F[Option[Response[F]]]`). 
There is a type defined for this in the `http4s.server` package:

```scala
type AuthMiddleware[F[_], T] = Middleware[OptionT[F, *], AuthedRequest[F, T], Response[F], Request[F], Response[F]]
```
See the [Authentication] documentation for more details.

## Composing Services with Middleware
Since middleware returns a [`Kleisli`], you can compose it with another middleware.
Additionally, you can compose services before applying the middleware function, 
and/or compose services with the service obtained by applying some middleware function. 
For example:

```scala mdoc:silent
val apiService = HttpRoutes.of[IO] {
  case GET -> Root / "api" =>
    Ok()
}

val anotherService = HttpRoutes.of[IO] {
  case GET -> Root / "another" =>
    Ok()
}

val aggregateService = apiService <+> MyMiddle(service <+> anotherService, "SomeKey" -> "SomeValue")

val apiRequest = Request[IO](Method.GET, uri"/api")
```

```scala mdoc
aggregateService.orNotFound(goodRequest).unsafeRunSync()
aggregateService.orNotFound(apiRequest).unsafeRunSync()
```

Note that `goodRequest` ran through the `MyMiddle` middleware and the `Result` had
our header added to it. But, `apiRequest` did not go through the middleware and did
not have the header added to it's `Result`.

## Included Middleware
Http4s includes some middleware Out of the Box in the `org.http4s.server.middleware`
package. These include:

* [Authentication]
* Cross Origin Resource Sharing ([CORS])
* Response Compression ([GZip])
* [Service Timeout]
* [Jsonp]
* [Virtual Host]
* [Metrics]
* [`X-Request-ID` header]

And a few others.

### Metrics Middleware

Apart from the middleware mentioned in the previous section. There is, as well,
Out of the Box middleware for [Dropwizard](https://http4s.github.io/http4s-dropwizard-metrics/) and [Prometheus](https://http4s.github.io/http4s-prometheus-metrics/) metrics.

### X-Request-ID Middleware

Use the `RequestId` middleware to automatically generate a `X-Request-ID` header to a request,
if one wasn't supplied. Adds a `X-Request-ID` header to the response with the id generated
or supplied as part of the request.

This [heroku guide](https://devcenter.heroku.com/articles/http-request-id) gives a brief explanation
as to why this header is useful.

```scala mdoc:silent
import org.http4s.server.middleware.RequestId
import org.typelevel.ci._

val requestIdService = RequestId.httpRoutes(HttpRoutes.of[IO] {
  case req =>
    val reqId = req.headers.get(ci"X-Request-ID").fold("null")(_.head.value)
    // use request id to correlate logs with the request
    IO(println(s"request received, cid=$reqId")) *> Ok()
})
val responseIO = requestIdService.orNotFound(goodRequest)
```

Note: `req.attributes.lookup(RequestId.requestIdAttrKey)` can also be used to lookup the request id
extracted from the header, or the generated request id.

```scala mdoc
// generated request id can be correlated with logs
val resp = responseIO.unsafeRunSync()
// X-Request-ID header added to response
resp.headers
// the request id is also available using attributes
resp.attributes.lookup(RequestId.requestIdAttrKey)
```

[service]: service.md
[dsl]: dsl.md
[Authentication]: auth.md
[CORS]: cors.md
[GZip]: gzip.md
[HSTS]: hsts.md
[Service Timeout]: @API_URL@/org/http4s/server/middleware/Timeout$
[Jsonp]: @API_URL@/org/http4s/server/middleware/Jsonp$
[Virtual Host]: @API_URL@/org/http4s/server/middleware/VirtualHost$
[Metrics]: @API_URL@/org/http4s/server/middleware/Metrics$
[`X-Request-ID` header]: @API_URL@/org/http4s/server/middleware/RequestId$
[`Kleisli`]: https://typelevel.org/cats/datatypes/kleisli.html
