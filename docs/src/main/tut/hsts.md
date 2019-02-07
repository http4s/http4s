---
menu: main
title: HSTS
weight: 126
---

Http4s provides a [Middleware] giving support for *HTTP Strict Transport Security (HSTS)*.
The middleware is called `HSTS` and simply adds a header to enable a HSTS security policy.
Though it is not enforced, HSTS only makes sense for an `https` service.

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
import org.http4s.dsl.io._
import org.http4s.implicits._
import cats.effect.IO
```

Let's make a simple service that will be exposed and wrapped with HSTS.

```tut:book
val service = HttpRoutes.of[IO] {
  case _ =>
    Ok("ok")
}

val request = Request[IO](Method.GET, Uri.uri("/"))

// Do not call 'unsafeRunSync' in your code
val response = service.orNotFound(request).unsafeRunSync
response.headers
```

If we were to wrap this on the `HSTS` middleware.

```tut:silent
import org.http4s.server.middleware._
```

```tut:book
val hstsService = HSTS(service)

// Do not call 'unsafeRunSync' in your code
val response = hstsService.orNotFound(request).unsafeRunSync
response.headers
```

Now the response has the `Strict-Transport-Security` header which will mandate browsers
supporting HSTS to always connect using `https`.

As described in [Middleware], services and middleware can be composed though HSTS
is something you may want enabled across all your routes.

## Configuration

By default `HSTS` is configured to indicate that all requests during 1 year
should be done over `https` and it will contain the `includeSubDomains` directive by default.

If you want to `preload` or change other default values you can pass a custom header, e.g.

```tut:silent
import org.http4s.headers._
import scala.concurrent.duration._
```

```tut:book
val hstsHeader = `Strict-Transport-Security`.unsafeFromDuration(30.days, includeSubDomains = true, preload = true)
val hstsService = HSTS(service, hstsHeader)

// Do not call 'unsafeRunSync' in your code
val response = hstsService.orNotFound(request).unsafeRunSync
response.headers
```

## References

* [RFC-6797](https://tools.ietf.org/html/rfc6797)
* [HSTS Cheat Sheet](https://www.owasp.org/index.php/HTTP_Strict_Transport_Security_Cheat_Sheet)

[Middleware]: ../middleware
