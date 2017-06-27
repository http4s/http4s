---
menu: tut
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

```tut:silent
import org.http4s._
import org.http4s.dsl._
```

Then, we can create a middleware that adds a header to successful responses from
the wrapped service like this.

```tut:book
def myMiddle(service: HttpService, header: Header): HttpService = Service.lift { req =>
  service(req).map {
    case Status.Successful(resp) =>
      resp.putHeaders(header)
    case resp =>
      resp
  }
}
```

All we do here is pass the request to the service,
which returns a `Task[Response]`. So, we use `map` to get the request out of the task,
add the header if the response is a success, and then pass the response on. We could
just as easily modify the request before we passed it to the service.

Now, let's create a simple service. As mentioned between [service] and [dsl], because `Service`
is implemented as a [`Kleisli`], which is just a function at heart, we can test a
service without a server. Because an `HttpService` returns a `Task[Response]`,
we need to call `unsafeRun` on the result of the function to extract the `Response`.

```tut:book
val service = HttpService {
  case GET -> Root / "bad" =>
    BadRequest()
  case _ =>
    Ok()
}

val goodRequest = Request(Method.GET, uri("/"))
val badRequest = Request(Method.GET, uri("/bad"))

service(goodRequest).unsafeRun
service(badRequest).unsafeRun
```

Now, we'll wrap the service in our middleware to create a new service, and try it out.

```tut:book
val wrappedService = myMiddle(service, Header("SomeKey", "SomeValue"));

wrappedService(goodRequest).unsafeRun
wrappedService(badRequest).unsafeRun
```

Note that the successful response has your header added to it.

If you intend to use you middleware in multiple places,  you may want to implement
it as an `object` and use the `apply` method.

```tut:book
import fs2.interop.cats._

object MyMiddle {
  def addHeader(mResp: MaybeResponse, header: Header) =
    mResp match {
      case Status.Successful(resp) => resp.putHeaders(header)
      case resp => resp
    }

  def apply(service: HttpService, header: Header) =
    service.map(addHeader(_, header))
}

val newService = MyMiddle(service, Header("SomeKey", "SomeValue"))

newService(goodRequest).unsafeRun
newService(badRequest).unsafeRun
```

It is possible for the wrapped `Service` to have different `Request` and `Response`
types than the middleware. Authentication is, again, a good example. Authentication
middleware is an `HttpService` (an alias for `Service[Request, Response]`) that wraps an `
AuthedService` (an alias for `Service[AuthedRequest[T], Response]`. There is a type
defined for this in the `http4s.server` package:

```scala
type AuthMiddleware[T] = Middleware[AuthedRequest[T], Response, Request, Response]
```
See the [Authentication] documentation for more details.

## Composing Services with Middleware
Because middleware returns a `Service`, you can compose services wrapped in
middleware with other, unwrapped, services, or services wrapped in other middleware.
You can also wrap a single service in multiple layers of middleware. For example:

```tut:book
val apiService = HttpService {
  case GET -> Root / "api" =>
    Ok()
}

import cats.implicits._
val aggregateService = apiService |+| MyMiddle(service, Header("SomeKey", "SomeValue"))

val apiRequest = Request(Method.GET, uri("/api"))

aggregateService(goodRequest).unsafeRun
aggregateService(apiRequest).unsafeRun
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

And a few others.

[service]: ../service
[dsl]: ../dsl
[Authentication]: ../auth
[CORS]: ../cors
[GZip]: ../gzip
[Service Timeout]: ../api/org/http4s/server/middleware/Timeout$
[Jsonp]: ../api/org/http4s/server/middleware/Jsonp$
[Virtual Host]: ../api/org/http4s/server/middleware/VirtualHost$
[`Kleisli`]: http://typelevel.org/cats/datatypes/kleisli.html
