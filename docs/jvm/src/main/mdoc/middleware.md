---
menu: main
title: Middleware
weight: 115
---

A middleware is a wrapper around a [service] that provides a means of manipulating
the `Request` sent to service, and/or the `Response` returned by the service. In
some cases, such as [Authentication], middleware may even prevent the service
from being called.

At its most basic, middleware is simply a function that takes one `Service` as a
parameter and returns another `Service`. In addition to the `Service`, the middleware
function can take any additional parameters it needs to perform its task. Let's look
at a simple example.

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

```scala mdoc:silent:nest
import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
```

Then, we can create a middleware that adds a header to successful responses from
the wrapped service like this.

```scala mdoc
def myMiddle(service: HttpRoutes[IO], header: Header.ToRaw): HttpRoutes[IO] = Kleisli { (req: Request[IO]) =>
  service(req).map {
    case Status.Successful(resp) =>
      resp.putHeaders(header)
    case resp =>
      resp
  }
}
```

All we do here is pass the request to the service,
which returns an `F[Response]`. So, we use `map` to get the request out of the task,
add the header if the response is a success, and then pass the response on. We could
just as easily modify the request before we passed it to the service.

Now, let's create a simple service. As mentioned between [service] and [dsl], because `Service`
is implemented as a [`Kleisli`], which is just a function at heart, we can test a
service without a server. Because an `HttpService[F]` returns a `F[Response[F]]`,
we need to call `unsafeRunSync` on the result of the function to extract the `Response[F]`.

```scala mdoc
val service = HttpRoutes.of[IO] {
  case GET -> Root / "bad" =>
    BadRequest()
  case _ =>
    Ok()
}

val goodRequest = Request[IO](Method.GET, uri"/")
val badRequest = Request[IO](Method.GET, uri"/bad")

service.orNotFound(goodRequest).unsafeRunSync()
service.orNotFound(badRequest).unsafeRunSync()
```

Now, we'll wrap the service in our middleware to create a new service, and try it out.

```scala mdoc
val wrappedService = myMiddle(service, "SomeKey" -> "SomeValue");

wrappedService.orNotFound(goodRequest).unsafeRunSync()
wrappedService.orNotFound(badRequest).unsafeRunSync()
```

Note that the successful response has your header added to it.

If you intend to use you middleware in multiple places,  you may want to implement
it as an `object` and use the `apply` method.

```scala mdoc
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

newService.orNotFound(goodRequest).unsafeRunSync()
newService.orNotFound(badRequest).unsafeRunSync()
```

It is possible for the wrapped `Service` to have different `Request` and `Response`
types than the middleware. Authentication is, again, a good example. Authentication
middleware is an `HttpService` (an alias for `Service[Request, Response]`) that wraps an `
AuthedService` (an alias for `Service[AuthedRequest[T], Response]`. There is a type
defined for this in the `http4s.server` package:

```scala
type AuthMiddleware[F, T] = Middleware[AuthedRequest[F, T], Response[F], Request[F], Response[F]]
```
See the [Authentication] documentation for more details.

## Composing Services with Middleware
Because middleware returns a `Service`, you can compose services wrapped in
middleware with other, unwrapped, services, or services wrapped in other middleware.
You can also wrap a single service in multiple layers of middleware. For example:

```scala mdoc
val apiService = HttpRoutes.of[IO] {
  case GET -> Root / "api" =>
    Ok()
}

val aggregateService = apiService <+> MyMiddle(service, "SomeKey" -> "SomeValue")

val apiRequest = Request[IO](Method.GET, uri"/api")

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
Out of the Box middleware for Dropwizard and Prometheus metrics

#### Dropwizard Metrics Middleware

To make use of this metrics middleware the following dependencies are needed:

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-server" % http4sVersion,
  "org.http4s" %% "http4s-dropwizard-metrics" % http4sVersion
)
```

We can create a middleware that registers metrics prefixed with a
provided prefix like this.

```scala mdoc:silent
import org.http4s.server.middleware.Metrics
import org.http4s.metrics.dropwizard.Dropwizard
import com.codahale.metrics.SharedMetricRegistries
```
```scala mdoc
val registry = SharedMetricRegistries.getOrCreate("default")

val meteredRoutes = Metrics[IO](Dropwizard(registry, "server"))(apiService)
```

#### Prometheus Metrics Middleware

To make use of this metrics middleware the following dependencies are needed:

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-server" % http4sVersion,
  "org.http4s" %% "http4s-prometheus-metrics" % http4sVersion
)
```

We can create a middleware that registers metrics prefixed with a
provided prefix like this.

```scala mdoc:silent
import cats.effect.{IO, Resource}
import org.http4s.HttpRoutes
import org.http4s.metrics.prometheus.{Prometheus, PrometheusExportService}
import org.http4s.server.Router
import org.http4s.server.middleware.Metrics
```
```scala mdoc:nest
val meteredRouter: Resource[IO, HttpRoutes[IO]] =
  for {
    metricsSvc <- PrometheusExportService.build[IO]
    metrics <- Prometheus.metricsOps[IO](metricsSvc.collectorRegistry, "server")
    router = Router[IO](
      "/api" -> Metrics[IO](metrics)(apiService),
      "/" -> metricsSvc.routes
    )
  } yield router
```

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

[service]: ../service
[dsl]: ../dsl
[Authentication]: ../auth
[CORS]: ../cors
[GZip]: ../gzip
[HSTS]: ../hsts
[Service Timeout]: ../api/org/http4s/server/middleware/Timeout$
[Jsonp]: ../api/org/http4s/server/middleware/Jsonp$
[Virtual Host]: ../api/org/http4s/server/middleware/VirtualHost$
[Metrics]: ../api/org/http4s/server/middleware/Metrics$
[`X-Request-ID` header]: ../api/org/http4s/server/middleware/RequestId$
[`Kleisli`]: https://typelevel.org/cats/datatypes/kleisli.html
