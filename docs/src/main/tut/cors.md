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

```tut:silent
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
```

Let's start by making a simple service.

```tut:book
val service = HttpRoutes.of[IO] {
  case _ =>
    Ok()
}

val request = Request[IO](Method.GET, Uri.uri("/"))

service.orNotFound(request).unsafeRunSync
```

Now we can wrap the service in the `CORS` middleware.

```tut:silent
import org.http4s.server.middleware._
```

```tut:book
val corsService = CORS(service)

corsService.orNotFound(request).unsafeRunSync
```

So far, there was no change. That's because an `Origin` header is required
in the requests and it must include a scheme. This, of course, is the responsibility of the caller.

```tut:book
val originHeader = Header("Origin", "https://somewhere.com")
val corsRequest = request.putHeaders(originHeader)

corsService.orNotFound(corsRequest).unsafeRunSync
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

```tut:book
val googleGet = Request[IO](Method.GET, Uri.uri("/"), headers = Headers(Header("Origin", "https://google.com")))
val yahooPut = Request[IO](Method.PUT, Uri.uri("/"), headers = Headers(Header("Origin", "https://yahoo.com")))
val duckPost = Request[IO](Method.POST, Uri.uri("/"), headers = Headers(Header("Origin", "https://duckduckgo.com")))
```

Now, we'll create a configuration that limits the allowed methods to `GET`
and `POST`, pass that to the `CORS` middleware, and try it out on our requests.

```tut:silent
import scala.concurrent.duration._
```

```tut:book
val methodConfig = CORSConfig(
  anyOrigin = true,
  anyMethod = false,
  allowedMethods = Some(Set("GET", "POST")),
  allowCredentials = true,
  maxAge = 1.day.toSeconds)

val corsMethodSvc = CORS(service, methodConfig)

corsMethodSvc.orNotFound(googleGet).unsafeRunSync
corsMethodSvc.orNotFound(yahooPut).unsafeRunSync
corsMethodSvc.orNotFound(duckPost).unsafeRunSync
```

As you can see, the CORS headers were only added to the `GET` and `POST` requests.
Next, we'll create a configuration that limits the origins to "yahoo.com" and
"duckduckgo.com". allowedOrigins can use any expression that resolves into a boolean.

```tut:book
val originConfig = CORSConfig(
  anyOrigin = false,
  allowedOrigins = Set("https://yahoo.com", "https://duckduckgo.com"),
  allowCredentials = false,
  maxAge = 1.day.toSeconds)

val corsOriginSvc = CORS(service, originConfig)

corsOriginSvc.orNotFound(googleGet).unsafeRunSync
corsOriginSvc.orNotFound(yahooPut).unsafeRunSync
corsOriginSvc.orNotFound(duckPost).unsafeRunSync
```

Again, the results are as expected. You can, of course, create a configuration that
combines limits on both HTTP method and origin.

As described in [Middleware], services and middleware can be composed such
that only some of your endpoints are CORS enabled.

[Middleware]: ../middleware
