---
menu: tut
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
import org.http4s._
import org.http4s.dsl._
```

Let's start by making a simple service.

```tut:book
val service = HttpService {
  case _ =>
    Ok()
}

val request = Request(Method.GET, uri("/"))

service(request).unsafeRun
```

Now we can wrap the service in the `CORS` middleware.

```tut:book
import org.http4s.server.middleware._
val corsService = CORS(service)

corsService(request).unsafeRun
```

So far, there was no change. That's because an `Origin` header is required
in the requests. This, of course, is the responsibility of the caller.

```tut:book
val originHeader = Header("Origin", "somewhere.com")
val corsRequest = request.putHeaders(originHeader)

corsService(corsRequest).unsafeRun
```

Notice how the response has the CORS headers added. How easy was
that? And, as described in [Middleware], services and middleware can be
composed such that only some of your endpoints are CORS enabled.

## Configuration
The example above showed the default configuration for CORS, which adds the
headers to any successul response, regardless of origin or HTTP method. There
are configuration options to modify that.

First, we'll create some requests to use in our example. We want these requests
have a variety of origins and methods.

```tut:book
val googleGet = Request(Method.GET, uri("/"), headers = Headers(Header("Origin", "google.com")))
val yahooPut = Request(Method.PUT, uri("/"), headers = Headers(Header("Origin", "yahoo.com")))
val duckPost = Request(Method.POST, uri("/"), headers = Headers(Header("Origin", "duckduckgo.com")))
```

Now, we'll create a configuration that limits the allowed methods to `GET`
and `POST`, pass that to the `CORS` middleware, and try it out on our requests.

```tut:book
import scala.concurrent.duration._

val methodConfig = CORSConfig(
  anyOrigin = true,
  anyMethod = false,
  allowedMethods = Some(Set("GET", "POST")),
  allowCredentials = true,
  maxAge = 1.day.toSeconds)

val corsMethodSvc = CORS(service, methodConfig)

corsMethodSvc(googleGet).unsafeRun
corsMethodSvc(yahooPut).unsafeRun
corsMethodSvc(duckPost).unsafeRun
```

As you can see, the CORS headers were only added to the `GET` and `POST` requests.
Next, we'll create a configuration that limits the origins to "yahoo.com" and
"duckduckgo.com". allowedOrigins can use any expression that resolves into a boolean.

```tut:book
val originConfig = CORSConfig(
  anyOrigin = false,
  allowedOrigins = origin => Set("yahoo.com", "duckduckgo.com").contains(origin),
  allowCredentials = false,
  maxAge = 1.day.toSeconds)

val corsOriginSvc = CORS(service, originConfig)

corsOriginSvc(googleGet).unsafeRun
corsOriginSvc(yahooPut).unsafeRun
corsOriginSvc(duckPost).unsafeRun
```

Again, the results are as expected. You can, of course, create a configuration that
combines limits on both HTTP method and origin.

As described in [Middleware], services and middleware can be composed such
that only some of your endpoints are CORS enabled.

[Middleware]: ../middleware
