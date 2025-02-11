# GZip Compression

Http4s provides [Middleware], named `GZip`, for allowing for the compression of the `Response`
body and [Middleware], named `GUnzip`, for the decompression of the incoming `Request` body.

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

Let's start by making a simple service that returns a (relatively) large string
in its body. We'll use `as[String]` to examine the body.

```scala mdoc:silent
val service = HttpRoutes.of[IO] {
  case _ =>
    Ok("I repeat myself when I'm under stress. " * 3)
}

val request = Request[IO](Method.GET, uri"/")
```

```scala mdoc
// Do not call 'unsafeRun' in your code - see note at bottom.
val response = service.orNotFound(request).unsafeRunSync()
val body = response.as[String].unsafeRunSync()
body.length
```

Now we can wrap the service in the `GZip` middleware.

```scala mdoc:silent
import org.http4s.server.middleware._
val serviceZip = GZip(service)
```

```scala mdoc
// Do not call 'unsafeRun' in your code - see note at bottom.
val respNormal = serviceZip.orNotFound(request).unsafeRunSync()
val bodyNormal = respNormal.as[String].unsafeRunSync()
bodyNormal.length
```

So far, there was no change. That's because the caller needs to inform us that
they will accept GZipped responses via an `Accept-Encoding` header. Acceptable
values for the `Accept-Encoding` header are **"gzip"**, **"x-gzip"**, and **"*"**.

```scala mdoc
val requestZip = request.putHeaders("Accept-Encoding" -> "gzip")

// Do not call 'unsafeRun' in your code - see note at bottom.
val respZip = serviceZip.orNotFound(requestZip).unsafeRunSync()
val bodyZip = respZip.as[String].unsafeRunSync()
bodyZip.length
```

Notice how the response no longer looks very String-like and it's shorter in
length. Also, there is a `Content-Encoding` header in the response with a value
of **"gzip"**.

As described in [Middleware], services and middleware can be composed such
that only some of your endpoints are `GZip` enabled.

## Decompressing Request

There is a separate `GUnzip` middleware that supports decompressing request.
Let's see how it works with a simple echo service, returning the body of incoming requests.

```scala mdoc:reset:invisible
import cats.effect._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._

import cats.effect.unsafe.IORuntime
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
```

```scala mdoc:silent
val service = HttpRoutes.of[IO] {
  case req =>
    Ok(req.body)
}

val request = Request[IO](Method.POST, uri"/").withEntity("echo")
```

```scala mdoc
// Do not call 'unsafeRun' in your code - see note at bottom.
val response = service.orNotFound(request).unsafeRunSync()
val body = response.as[String].unsafeRunSync()
body.length
```

Now let's see what happens when we wrap the service with `GUnzip` middleware.
For the purpose of this example, let's create a compressed body using
`Compression` utility from [fs2](https://fs2.io) library.

```scala mdoc:silent
import fs2._
import fs2.compression._

val compressedEntity = 
  Stream
    .emits(("I repeat myself when I'm under stress. " * 3).getBytes())
    .through(Compression[IO].gzip())

val compressedRequest = request.withEntity(compressedEntity)
```

Now, similarly to `GZip`, let's wrap the service with the `GUnzip` middleware.

```scala mdoc:silent
import org.http4s.server.middleware._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.Slf4jFactory

implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create
val serviceUnzip = GUnzip(service)
```

```scala mdoc
// Do not call 'unsafeRun' in your code - see note at bottom.
val respNormal = serviceUnzip.orNotFound(compressedRequest).unsafeRunSync()
val bodyNormal = respNormal.as[String].unsafeRunSync()
bodyNormal.length
```

We can clearly see the middleware didn't do much and that's because the decompression will only be
triggered if the request contains `Content-Encoding` header with a value of **"gzip"** or **"x-gzip"**.

```scala mdoc
// Do not call 'unsafeRun' in your code - see note at bottom.
val validRequest = compressedRequest.putHeaders("Content-Encoding" -> "gzip")

val respDecompressed = serviceUnzip.orNotFound(validRequest).unsafeRunSync()
val bodyDecompressed = respDecompressed.as[String].unsafeRunSync()
bodyDecompressed.length
```

This time we can see that the request got decompressed as expected.

**NOTE:** In this documentation, we are calling `unsafeRunSync` to extract values out
of a service or middleware code. You can work with values while keeping them inside the
`F` using `map`, `flatMap` and/or `for`. Remember, your service returns an
`F[Response]`.

[Middleware]: middleware.md
