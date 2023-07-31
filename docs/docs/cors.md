# CORS

For security reasons, modern web browsers enforce a [same origin policy](https://developer.mozilla.org/en-US/docs/Web/Security/Same-origin_policy),
restricting the ability of sites from a given [origin](https://developer.mozilla.org/en-US/docs/Glossary/Origin)
to access resources at a different origin. Http4s provides [Middleware], named `CORS`, for adding the appropriate headers
to responses to allow limited exceptions to this via [cross origin resource sharing](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS).

@:callout(warning)
This guide assumes you are already familiar with CORS and its attendant security risks.
By enabling CORS you are bypassing an important protection against malicious third-party
websites - before doing so for any potentially sensitive resource, make sure you understand
what you are doing and why.
@:@

## Usage
Examples in this document have the following dependencies.

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-server" % http4sVersion
)
```

And we need some imports.

```scala mdoc:silent
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
```

If you're in a REPL, we also need a runtime:

```scala mdoc:silent
import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
```

Let's start by making a simple service.

```scala mdoc:silent
val service = HttpRoutes.of[IO] {
  case _ => Ok()
}

val request = Request[IO](Method.GET, uri"/")
```


```scala mdoc
service.orNotFound(request).unsafeRunSync()
```

Now we can wrap the service in the `CORS` middleware.

```scala mdoc:silent
import org.http4s.server.middleware._

val corsService = CORS.policy.withAllowOriginAll(service)
```

```scala mdoc
corsService.orNotFound(request).unsafeRunSync()
```

So far, there was no change. That's because an `Origin` header is required
in the requests and it must include a scheme. This, of course, is the responsibility of the caller.

```scala mdoc
val corsRequest = request.putHeaders("Origin" -> "https://somewhere.com")

corsService.orNotFound(corsRequest).unsafeRunSync()
```

Notice how the response has the CORS headers added. How easy was
that? And, as described in [Middleware], services and middleware can be
composed such that only some of your endpoints are CORS enabled.

## Configuration
The example above showed one basic configuration for CORS, which adds the
headers to any successful response, regardless of origin or HTTP method. There
are configuration options to modify that.

First, we'll create some requests to use in our example. We want these requests
have a variety of origins and methods.

```scala mdoc
val googleGet = Request[IO](Method.GET, uri"/",
  headers = Headers("Origin" -> "https://google.com"))
val yahooPut = Request[IO](Method.PUT, uri"/",
  headers = Headers("Origin" -> "https://yahoo.com"))
val duckPost = Request[IO](Method.POST, uri"/",
  headers = Headers("Origin" -> "https://duckduckgo.com"))
```

Now, we'll create a configuration that limits the allowed methods to `GET`
and `POST`, pass that to the `CORS` middleware, and try it out on our requests.

```scala mdoc:silent
import scala.concurrent.duration._

val corsMethodSvc = CORS.policy
  .withAllowOriginAll
  .withAllowMethodsIn(Set(Method.GET, Method.POST))
  .withAllowCredentials(false)
  .withMaxAge(1.day)
  .apply(service)
```

```scala mdoc
corsMethodSvc.orNotFound(googleGet).unsafeRunSync()
corsMethodSvc.orNotFound(yahooPut).unsafeRunSync()
corsMethodSvc.orNotFound(duckPost).unsafeRunSync()
```

As you can see, the CORS headers were only added to the `GET` and `POST` requests.
Next, we'll create a configuration that limits the origins to "yahoo.com" and
"duckduckgo.com". `withAllowOriginHost` accepts an `Origin.Host => Boolean`.
If you're simply enumerating allowed hosts, a `Set` is convenient:

```scala mdoc:silent
import org.http4s.headers.Origin

val corsOriginSvc = CORS.policy
  .withAllowOriginHost(Set(
    Origin.Host(Uri.Scheme.https, Uri.RegName("yahoo.com"), None),
    Origin.Host(Uri.Scheme.https, Uri.RegName("duckduckgo.com"), None)
  ))
  .withAllowCredentials(false)
  .withMaxAge(1.day)
  .apply(service)
```

```scala mdoc
corsOriginSvc.orNotFound(googleGet).unsafeRunSync()
corsOriginSvc.orNotFound(yahooPut).unsafeRunSync()
corsOriginSvc.orNotFound(duckPost).unsafeRunSync()
```

Again, the results are as expected. You can, of course, create a configuration that
combines limits on HTTP method, origin, and headers.

As described in [Middleware], services and middleware can be composed such
that only some of your endpoints are CORS enabled.

[Middleware]: middleware.md
