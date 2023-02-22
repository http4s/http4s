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
implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global
val service = HttpRoutes.of[IO] {
  case GET -> Root / "bad" => BadRequest()
  case POST -> Root / "post" => Ok()
  case _ => Ok()
}

val goodRequest = Request[IO](Method.GET, uri"/")
val badRequest = Request[IO](Method.GET, uri"/bad")
val postRequest = Request[IO](Method.POST, uri"/post")
```

## Headers

### Caching
This middleware adds response headers so that clients know how to cache a response. It performs no caching.
Below is one example of usage, see [Caching] for more methods.

```scala mdoc:silent
import org.http4s.server.middleware.Caching
import scala.concurrent.duration._

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

Use the `RequestId` middleware to automatically generate a `X-Request-ID` header to a request,
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
### DefaultHead?
### HttpMethodOverrider
### HttpsRedirect?
### TranslateUri
### UrlFormLifter


## Scaling and management
### ConcurrentRequests
### EntityLimiter?
### MaxActiveRequests
### Throttle
### Timeout

## Error handling and Logging
### ErrorAction
### ErrorHandling
### Metrics
WIP

Apart from the middleware mentioned in the previous section. There is, as well,
Out of the Box middleware for [Dropwizard](https://http4s.github.io/http4s-dropwizard-metrics/) and [Prometheus](https://http4s.github.io/http4s-prometheus-metrics/) metrics.


### RequestLogger, ResponseLogger, Logger

## Advanced
### BodyCache
### BracketRequest
### ChunkAggregator
### Jsonp
### ContextMiddleware

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

[service]: service.md
[dsl]: dsl.md
[Authentication]: auth.md
[CORS]: cors.md
[GZip]: gzip.md
[HSTS]: hsts.md
[Service Timeout]: @API_URL@/org/http4s/server/middleware/Timeout$
[Jsonp]: @API_URL@/org/http4s/server/middleware/Jsonp$
[Virtual Host]: @API_URL@/org/http4s/server/middleware/VirtualHost$
[Metrics]: @API_URL@/org/http4s/server/middleware/Metrics$
[`X-Request-ID` header]: @API_URL@/org/http4s/server/middleware/RequestId$
[Caching]: @API_URL@org/http4s/server/middleware/Caching$.html
