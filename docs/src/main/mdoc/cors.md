---
menu: main
title: CORS
weight: 122
---

Http4s provides [Middleware], named `CORS`, for adding the appropriate headers
to responses to allow Cross Origin Resource Sharing.

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

```scala mdoc:silent:nest
import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
```

Let's start by making a simple service.

```scala mdoc
val service = HttpRoutes.of[IO] {
  case _ =>
    Ok()
}

val request = Request[IO](Method.GET, uri"/")

service.orNotFound(request).unsafeRunSync()
```

Now we can wrap the service in the `CORS` middleware.

```scala mdoc:silent
import org.http4s.server.middleware._
```

```scala mdoc
val corsService = CORS(service)

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
The example above showed the default configuration for CORS, which adds the
headers to any successful response, regardless of origin or HTTP method. There
are configuration options to modify that.

First, we'll create some requests to use in our example. We want these requests
have a variety of origins and methods.

```scala mdoc
val googleGet = Request[IO](Method.GET, uri"/", headers = Headers("Origin" -> "https://google.com"))
val yahooPut = Request[IO](Method.PUT, uri"/", headers = Headers("Origin" -> "https://yahoo.com"))
val duckPost = Request[IO](Method.POST, uri"/", headers = Headers("Origin" -> "https://duckduckgo.com"))
```

Now, we'll create a configuration that limits the allowed methods to `GET`
and `POST`, pass that to the `CORS` middleware, and try it out on our requests.

```scala mdoc:silent
import scala.concurrent.duration._
```

```scala mdoc
val methodConfig = CORSConfig.default
                     .withAnyOrigin(false)
                     .withAnyMethod(false)
                     .withAllowedMethods(Some(Set(Method.GET, Method.POST)))
                     .withAllowCredentials(true)
                     .withMaxAge(1.seconds)

val corsMethodSvc = CORS(service, methodConfig)

corsMethodSvc.orNotFound(googleGet).unsafeRunSync()
corsMethodSvc.orNotFound(yahooPut).unsafeRunSync()
corsMethodSvc.orNotFound(duckPost).unsafeRunSync()
```

As you can see, the CORS headers were only added to the `GET` and `POST` requests.
Next, we'll create a configuration that limits the origins to "yahoo.com" and
"duckduckgo.com". allowedOrigins can use any expression that resolves into a boolean.

```scala mdoc
val originConfig = CORSConfig.default
                     .withAnyOrigin(false)
                     .withAllowedOrigins(Set("https://yahoo.com", "https://duckduckgo.com"))
                     .withAllowCredentials(false)
                     .withMaxAge(1.seconds)

val corsOriginSvc = CORS(service, originConfig)

corsOriginSvc.orNotFound(googleGet).unsafeRunSync()
corsOriginSvc.orNotFound(yahooPut).unsafeRunSync()
corsOriginSvc.orNotFound(duckPost).unsafeRunSync()
```

Again, the results are as expected. You can, of course, create a configuration that
combines limits on both HTTP method and origin.

As described in [Middleware], services and middleware can be composed such
that only some of your endpoints are CORS enabled.

[Middleware]: ../middleware
