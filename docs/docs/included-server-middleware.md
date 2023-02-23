# Included Server Middleware

Http4s includes some middleware out of the box in the `org.http4s.server.middleware`
package. Some of it is documented in its own page:

* [Authentication]
* Cross Origin Resource Sharing ([CORS])
* Response Compression ([GZip])
* [HSTS]

We'll describe and provide examples for the remaining middleware, but first we set up our service:
```scala mdoc:silent
import cats.effect._
import cats.syntax.all._
import org.typelevel.ci._
import org.http4s._
import org.http4s.dsl.io._
import org.http4s.implicits._
import cats.effect.unsafe.IORuntime
import scala.concurrent.duration._

implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

object NameQueryParamMatcher extends QueryParamDecoderMatcher[String]("name")

val service = HttpRoutes.of[IO] {
  case GET -> Root / "bad" => BadRequest()
  case GET -> Root / "ok" => Ok()
  case POST -> Root / "post" => Ok()
  case GET -> Root / "b" / "c" => Ok()
  case POST -> Root / "queryForm" :? NameQueryParamMatcher(name) => Ok(s"hello $name")
  case GET -> Root / "wait" => IO.sleep(10.millis) >> Ok()
}

val goodRequest = Request[IO](Method.GET, uri"/ok")
val badRequest = Request[IO](Method.GET, uri"/bad")
val postRequest = Request[IO](Method.POST, uri"/post")
val waitRequest = Request[IO](Method.GET, uri"/wait")
```

## Headers

### Caching
This middleware adds response headers so that clients know how to cache a response. It performs no server-side caching.
Below is one example of usage, see [Caching] for more methods.

```scala mdoc:silent
import org.http4s.server.middleware.Caching

val cacheService = Caching.cache(
  3.hours,
  isPublic = Left(CacheDirective.public),
  methodToSetOn = _ == Method.GET,
  statusToSetOn = _.isSuccess,
  service
).orNotFound

```
```scala mdoc
cacheService(goodRequest).unsafeRunSync().headers
cacheService(badRequest).unsafeRunSync().headers
cacheService(postRequest).unsafeRunSync().headers
```

### Date
Adds the current date to the response.

```scala mdoc:silent
import org.http4s.server.middleware.Date

val dateService = Date.httpRoutes(service).orNotFound
```
```scala mdoc
dateService(goodRequest).unsafeRunSync().headers
```

### HeaderEcho
Adds headers included in the request to the response.

```scala mdoc:silent
import org.http4s.server.middleware.HeaderEcho

val echoService = HeaderEcho.httpRoutes(echoHeadersWhen = _ => true)(service).orNotFound
```
```scala mdoc
echoService(goodRequest.putHeaders("Hello" -> "hi")).unsafeRunSync().headers
```

### ResponseTiming

Sets response header with the request duration.

```scala mdoc:silent
import org.http4s.server.middleware.ResponseTiming

val timingService = ResponseTiming(service.orNotFound)
```
```scala mdoc
timingService(goodRequest).unsafeRunSync().headers
```

### RequestId

Use the `RequestId` middleware to automatically generate a `X-Request-ID` header for a request,
if one wasn't supplied. Adds a `X-Request-ID` header to the response with the id generated
or supplied as part of the request.

This [heroku guide](https://devcenter.heroku.com/articles/http-request-id) gives a brief explanation
as to why this header is useful.

```scala mdoc:silent
import org.http4s.server.middleware.RequestId

val requestIdService = RequestId.httpRoutes(HttpRoutes.of[IO] {
  case req =>
    val reqId = req.headers.get(ci"X-Request-ID").fold("null")(_.head.value)
    // use request id to correlate logs with the request
    IO(println(s"request received, cid=$reqId")) *> Ok()
})
val responseIO = requestIdService.orNotFound(goodRequest)

// generated request id can be correlated with logs
val resp = responseIO.unsafeRunSync()
```

Note: `req.attributes.lookup(RequestId.requestIdAttrKey)` can also be used to lookup the request id
extracted from the header, or the generated request id.

```scala mdoc

// X-Request-ID header added to response
resp.headers
// the request id is also available using attributes
resp.attributes.lookup(RequestId.requestIdAttrKey)
```

### StaticHeaders

Adds static headers to the response.

```scala mdoc:silent
import org.http4s.server.middleware.StaticHeaders

val staticHeadersService = StaticHeaders(Headers("X-Hello" -> "hi"))(service).orNotFound
```
```scala mdoc
staticHeadersService(goodRequest).unsafeRunSync().headers
```

## Request rewriting

### AutoSlash

Removes a trailing slash from the requested url so that requests with trailing slash map to the route without.

```scala mdoc:silent
import org.http4s.server.middleware.AutoSlash

val autoSlashService = AutoSlash(service).orNotFound
val okWithSlash = Request[IO](Method.GET, uri"/ok/")
```
```scala mdoc
// without the middleware the request with trailing slash fails
service.orNotFound(goodRequest).unsafeRunSync().status
service.orNotFound(okWithSlash).unsafeRunSync().status

// with the middleware both work
autoSlashService(goodRequest).unsafeRunSync().status
autoSlashService(okWithSlash).unsafeRunSync().status
```

### DefaultHead
WIP

### HttpMethodOverrider

Allows a client to "disguise" the http verb of a request by indicating the desired verb somewhere else in the request.

```scala mdoc:silent
import org.http4s.server.middleware.HttpMethodOverrider
import org.http4s.server.middleware.HttpMethodOverrider.{HttpMethodOverriderConfig, QueryOverrideStrategy}

val overrideService = HttpMethodOverrider(
  service,
  HttpMethodOverriderConfig(
    QueryOverrideStrategy(paramName = "realMethod"),
    Set(Method.GET)
  )
).orNotFound
val overrideRequest = Request[IO](Method.GET, uri"/post?realMethod=POST")
```
```scala mdoc
service.orNotFound(overrideRequest).unsafeRunSync().status
overrideService(overrideRequest).unsafeRunSync().status
```

### HttpsRedirect
WIP

### TranslateUri
Removes a prefix from the path of the requested url.

```scala mdoc:silent
import org.http4s.server.middleware.TranslateUri

val translateService = TranslateUri(prefix = "a")(service).orNotFound
val translateRequest = Request[IO](Method.GET, uri"/a/b/c")
```
The following is successful even though `/b/c` is defined, and not `/a/b/c`:
```scala mdoc
translateService(translateRequest).unsafeRunSync().status
```

### UrlFormLifter

Transform `x-www-form-urlencoded` parameters into query parameters.

```scala mdoc:silent
import org.http4s.server.middleware.UrlFormLifter
import org.http4s.UrlForm

val urlFormService = UrlFormLifter.httpApp(service.orNotFound)
val formRequest = Request[IO](Method.POST, uri"/queryForm").withEntity(UrlForm.single("name", "John"))
```
Even though the `/queryForm` route takes query parameters, the form request works:
```scala mdoc
urlFormService(formRequest).flatMap(_.bodyText.compile.lastOrError).unsafeRunSync()
```

## Scaling and resource management

### ConcurrentRequests

```scala mdoc:silent
import org.http4s.server.middleware.ConcurrentRequests
import org.http4s.server.{ContextMiddleware, HttpMiddleware}
import org.http4s.ContextRequest
import cats.data.Kleisli

def dropContext[A](middleware: ContextMiddleware[IO, A]): HttpMiddleware[IO] =
  routes => middleware(Kleisli((c: ContextRequest[IO, A]) => routes(c.req)))

val concurrentService = ConcurrentRequests.route[IO](
  onIncrement => IO.println("someone comes to town"),
  onDecrement => IO.println("someone leaves town")
).map((middle: ContextMiddleware[IO, Long]) => dropContext(middle)(service).orNotFound)

```
TODO: coerce mdoc into showing the output
```scala mdoc
concurrentService.flatMap(svc => svc(waitRequest).race(svc(waitRequest))).void.unsafeRunSync()
```

### EntityLimiter

Ensures the request body is under a specific length. It does so by inspecting
the body, not by simply checking `Content-Lenght` (which could be spoofed).
This could be useful for file uploads, or to prevent attacks that exploit the
a service that loads the whole body into memory.

```scala mdoc:silent
import org.http4s.server.middleware.EntityLimiter

val limiterService = EntityLimiter.httpApp(service.orNotFound, limit = 16)
val smallRequest = postRequest.withEntity("*" * 15)
val bigRequest = postRequest.withEntity("*" * 16)
```
```scala mdoc
limiterService(smallRequest).map(_.status).unsafeRunSync()
limiterService(bigRequest).attempt.unsafeRunSync()
```

### MaxActiveRequests

Limit the number of active requests by rejecting requests over a certain limit.
This can be useful to ensure that your service remains responsive during high loads.

```scala mdoc:silent
import org.http4s.server.middleware.MaxActiveRequests

// creating the middleware is effectful
val maxService = MaxActiveRequests.forHttpApp[IO](maxActive = 2)
  .map(middleware => middleware(service.orNotFound))
```
Some requests will fail if the limit is reached:
```scala mdoc
maxService.flatMap(service =>
  List.fill(5)(waitRequest).parTraverse(req => service(req).map(_.status).attempt)
).unsafeRunSync()
```

### Throttle
Reject requests that exceed a given rate. An in-memory implementation of a
[TokenBucket], which refills at a given rate is provided, but other strategies
can be used.
Like `MaxActiveRequest` this can be used prevent a service from being affect
by high load.

```scala mdoc:silent
import org.http4s.server.middleware.Throttle

// creating the middleware is effectful because of the default token bucket
val throttleService = Throttle.httpApp[IO](amount = 1, per = 10.milliseconds)(service.orNotFound)
```
We'll submit request every 5ms and refill a token every 10ms:
```scala mdoc
throttleService.flatMap(service =>
  List.fill(5)(goodRequest).traverse(req =>
    IO.sleep(5.millis) >> service(req).map(_.status).attempt
  )
).unsafeRunSync()
```

#### Throttling with context
WIP - use context middleware + throttling to implement throttling by used

### Timeout

Limits how long the underlying service takes to respond. The service is
cancelled, if there are uncancelable effects they are completed and only
then is the response returned.

```scala mdoc:silent
import org.http4s.server.middleware.Timeout

val timeoutService = Timeout.httpApp[IO](timeout = 5.milliseconds)(service.orNotFound)
```
`/wait` takes 10 ms to finish so it's cancelled:
```scala mdoc
timeoutService(waitRequest).map(_.status).timed.unsafeRunSync()
```

## Error handling and Logging
### ErrorAction

WIP

### ErrorHandling

WIP

### Metrics
WIP

Apart from the middleware mentioned in the previous section. There is, as well,
Out of the Box middleware for [Dropwizard](https://http4s.github.io/http4s-dropwizard-metrics/) and [Prometheus](https://http4s.github.io/http4s-prometheus-metrics/) metrics.


### RequestLogger, ResponseLogger, Logger

## Advanced
### BodyCache

WIP

### BracketRequest

WIP

### ChunkAggregator

WIP

### Jsonp

WIP

### ContextMiddleware

This middleware allows extracting context from a request an propagating it down to the routes.

```scala mdoc:silent
import org.http4s.server.ContextMiddleware
import org.http4s.ContextRoutes
import cats.data.{Kleisli, OptionT}

// create a custom header
case class UserId(raw: String)
implicit val userIdHeader: Header[UserId, Header.Single] =
  Header.createRendered(ci"X-UserId", _.raw, s => Right(UserId(s)))

// middleware to read the user id from the request
val middleware = ContextMiddleware(
  Kleisli((r: Request[IO]) => OptionT.fromOption[IO](r.headers.get[UserId]))
)

// routes that expect a user id as context
val ctxRoutes = ContextRoutes.of[UserId, IO] {
  case GET -> Root / "ok" as userId => Ok(s"hello ${userId.raw}")
}

val contextService = middleware(ctxRoutes).orNotFound
val contextRequest = Request[IO](Method.GET, uri"/ok").putHeaders(UserId("Jack"))
```
```scala mdoc
contextService(contextRequest).flatMap(_.bodyText.compile.lastOrError).unsafeRunSync()
```

## Security
### CSRF

========================================================

* client mw
  * CookieJar - allows a client to store and supply cookies
  * DestinationAttribute
  * FollowRedirect - allows a client to follow redirects
  * GZip - allows a client to read gzip/deflate responses
  * Logger, RequestLogger, ResponseLogger - logs
  * Metrics - client metrics
  * Retry - retry requests
  * UnixSocket - ???

[Authentication]: auth.md
[CORS]: cors.md
[GZip]: gzip.md
[HSTS]: hsts.md
[Caching]: @API_URL@org/http4s/server/middleware/Caching$.html
[TokenBucket]: @API_URL@org/http4s/server/middleware/Throttle$$TokenBucket.html
