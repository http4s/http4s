---
menu: main
title: Middleware
weight: 115
---

A `Middleware` wraps a [`Service`]. Doing so allows you to change the `Request`
that is sent to a `Service`, and/or modify the `Response` that is returned by
that `Service`. In some cases, such as [Authentication], `Middleware` might
prevent the `Service` from being called.

`Middleware` is a function that takes one `Service` and returns a different
`Service`.

1. Add a dependency to SBT on the [dsl] by copying and pasting the following
   code to the existing `build.sbt` file:

```scala
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-dsl" % http4sVersion
)
```
1. Add the following import statements:

```tut:silent
import cats.effect._ // To import the IO type, see cats-effect documentation.
import cats.implicits._
import org.http4s._
import org.http4s.implicits._
import org.http4s.dsl.io._
```

1. Create a `Middleware` to add a header to each successful response returned by
wrapped `Service`:

```tut:book
def myMiddle(service: HttpService[IO], header: Header): HttpService[IO] = cats.data.Kleisli { req: Request[IO] =>
  service(req).map {
    case Status.Successful(resp) =>
      resp.putHeaders(header)
    case resp =>
      resp
  }
}
```

The preceding code passes the `Request` to the `Service`, which returns an
object of type `IO[Response]`. To add the header, change the `Request` inside
`IO[Request]` by using the `map` method. But the `Request` is only changed if it
is successful.

As mentioned in [service] and [dsl], `Service` is implemented as a [`Kleisli`].
A [`Kleisli`] is just a function, so we can test a service creating a full-blown
server. Because an `HttpService[F]` returns a `F[Response[F]]`, and not a
`Response[F]`, run `unsafeRunSync` on the result of the `Kleisli` function.

```tut:book
val service = HttpService[IO] {
  case GET -> Root / "bad" =>
    BadRequest()
  case _ =>
    Ok()
}

val goodRequest = Request[IO](Method.GET, uri("/"))
val badRequest = Request[IO](Method.GET, uri("/bad"))

service.orNotFound(goodRequest).unsafeRunSync
service.orNotFound(badRequest).unsafeRunSync
```

Wrap the service in our middleware to create a new `Service`, and invoke the
`unsafeRunSync` function.

```tut:book
val wrappedService = myMiddle(service, Header("SomeKey", "SomeValue"));

wrappedService.orNotFound(goodRequest).unsafeRunSync
wrappedService.orNotFound(badRequest).unsafeRunSync
```

Note that the successful response has the header added to it.

To reuse the header in multiple places, write an `object` with an apply method,
so it is reusable.

```tut:book
object MyMiddle {
  def addHeader(resp: Response[IO], header: Header) =
    resp match {
      case Status.Successful(resp) => resp.putHeaders(header)
      case resp => resp
    }

  def apply(service: HttpService[IO], header: Header) =
    service.map(addHeader(_, header))
}

val newService = MyMiddle(service, Header("SomeKey", "SomeValue"))

newService.orNotFound(goodRequest).unsafeRunSync
newService.orNotFound(badRequest).unsafeRunSync
```

It is possible for the wrapped `Service` to have different `Request` and
`Response` types than the `Middleware`. `Authentication` is, a good example.
`Authentication` `Middleware` is an `HttpService` (an alias for `Service[Request,Response]`)
that wraps an `AuthedService` (an alias for
`Service[AuthedRequest[T], Response]`. This type is defined in the
`http4s.server` package:

```scala
type AuthMiddleware[F, T] = Middleware[AuthedRequest[F, T], Response[F], Request[F], Response[F]]
```

See the [Authentication] documentation for more details.

## Composing Services with Middleware
Because `Middleware` returns a `Service`, you can compose services wrapped in
`Middleware` with other, unwrapped services, or services wrapped in other `Middleware`.
You can also wrap a single service in multiple layers of `Middleware`. For example:

```tut:book
val apiService = HttpService[IO] {
  case GET -> Root / "api" =>
    Ok()
}

val aggregateService = apiService <+> MyMiddle(service, Header("SomeKey", "SomeValue"))

val apiRequest = Request[IO](Method.GET, uri("/api"))

aggregateService.orNotFound(goodRequest).unsafeRunSync
aggregateService.orNotFound(apiRequest).unsafeRunSync
```

Note that `goodRequest` ran through the `MyMiddle` `Middleware` and the `Result` had
our header added to it. But, `apiRequest` did not go through the `Middleware` and did
not have the header added to its `Result`.

## Included Middleware
Http4s includes some `Middleware` Out of the Box in the `org.http4s.server.middleware`
package. These include:

* [Authentication]
* Cross Origin Resource Sharing ([CORS])
* Response Compression ([GZip])
* [Service Timeout]
* [Jsonp]
* [Virtual Host]

And a few others.

[`Service`]: ../service
[dsl]: ../dsl
[Authentication]: ../auth
[CORS]: ../cors
[GZip]: ../gzip
[HSTS]: ../hsts
[Service Timeout]: ../api/org/http4s/server/middleware/Timeout$
[Jsonp]: ../api/org/http4s/server/middleware/Jsonp$
[Virtual Host]: ../api/org/http4s/server/middleware/VirtualHost$
[`Kleisli`]: http://typelevel.org/cats/datatypes/kleisli.html
