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

```tut:silent
import org.http4s._
import org.http4s.dsl._
```

Let's start by making a simple service that returns a (relatively) large string
in it's body. We'll use `as[String]` to examine the body. The body is returned
as a `Task[String]`, so we need to call `run` to get the string itself.

```tut:book
val service = HttpService {
  case _ =>
    Ok("I repeat myself when I'm under stress. " * 3)
}

val request = Request(Method.GET, uri("/"))

val response = service(request).run
val body = response.as[String].run
body.length
```

Now we can wrap the service in the `GZip` middleware.

```tut:book
import org.http4s.server.middleware._
val zipService = GZip(service)

val response = zipService(request).run
val body = response.as[String].run
body.length
```

So far, there was no change. That's because the caller needs to inform us that
they will accept GZipped responses via an `Accept-Encoding` header. Acceptable 
values for the `Accept-Encoding` header are **"gzip"**, **"x-gzip"**, and **"*"**.

```tut:book
val acceptHeader = Header("Accept-Encoding", "gzip")
val zipRequest = request.putHeaders(acceptHeader)

val response = zipService(zipRequest).run
val body = response.as[String].run
body.length
```

Notice how the response no longer looks very String-like and it's shorter in 
length. Also, there is a `Content-Encoding` header in the response with a value
of **"gzip"**.

As described in [Middleware], services and middleware can be composed such 
that only some of your endpoints are GZip enabled.

[Middleware]: ../middleware