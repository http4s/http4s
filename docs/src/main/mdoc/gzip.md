---
menu: main
title: GZip Compression
weight: 124
---

Http4s provides [Middleware], named `GZip`, for allowing for the compression of the `Response`
body using GZip.

Examples in this document have the following dependencies.

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-server" % http4sVersion
)
```

And we need some imports.

```scala mdoc
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
```

Let's start by making a simple service that returns a (relatively) large string
in its body. We'll use `as[String]` to examine the body.

```scala mdoc
val service = HttpService[IO] {
  case _ =>
    Ok("I repeat myself when I'm under stress. " * 3)
}

val request = Request[IO](Method.GET, uri("/"))

// Do not call 'unsafeRun' in your code - see note at bottom.
val response1 = service.orNotFound(request).unsafeRunSync
val body1 = response1.as[String].unsafeRunSync
body1.length
```

Now we can wrap the service in the `GZip` middleware.

```scala mdoc
import org.http4s.server.middleware._
val zipService = GZip(service)

// Do not call 'unsafeRun' in your code - see note at bottom.
val response2 = zipService.orNotFound(request).unsafeRunSync
val body2 = response2.as[String].unsafeRunSync
body2.length
```

So far, there was no change. That's because the caller needs to inform us that
they will accept GZipped responses via an `Accept-Encoding` header. Acceptable
values for the `Accept-Encoding` header are **"gzip"**, **"x-gzip"**, and **"*"**.

```scala mdoc
import org.http4s.Header

val acceptHeader = Header("Accept-Encoding", "gzip")
val zipRequest = request.putHeaders(acceptHeader)

// Do not call 'unsafeRun' in your code - see note at bottom.
val response3 = zipService.orNotFound(zipRequest).unsafeRunSync
val body3 = response3.as[String].unsafeRunSync
body3.length
```

Notice how the response no longer looks very String-like and it's shorter in
length. Also, there is a `Content-Encoding` header in the response with a value
of **"gzip"**.

As described in [Middleware], services and middleware can be composed such
that only some of your endpoints are GZip enabled.

**NOTE:** In this documentation, we are calling `unsafeRunSync` to extract values out 
of a service or middleware code. You can work with values while keeping them inside the
`F` using `map`, `flatMap` and/or `for`. Remember, your service returns an
`F[Response]`.

[Middleware]: ../middleware
