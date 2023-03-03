# Included Server Middleware

Http4s includes some middleware out of the box in the `org.http4s.server.middleware`
package. Some of it is documented in its own page:

* [Authentication]
* Cross Origin Resource Sharing ([CORS])
* Response Compression ([GZip])
* [HSTS]
* [CSRF]

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
import cats.effect.std.Random
import fs2.Stream

implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

object NameQueryParamMatcher extends QueryParamDecoderMatcher[String]("name")

val service = HttpRoutes.of[IO] {
  case GET -> Root / "bad" => BadRequest()
  case GET -> Root / "ok" => Ok()
  case POST -> Root / "post" => Ok()
  case GET -> Root / "b" / "c" => Ok()
  case POST -> Root / "queryForm" :? NameQueryParamMatcher(name) => Ok(s"hello $name")
  case GET -> Root / "wait" => IO.sleep(10.millis) >> Ok()
  case GET -> Root / "boom" => IO.raiseError(new RuntimeException("boom!"))
  case r @ GET -> Root / "reverse" => r.decode[IO, String](s => Ok(s.reverse)) 
  case GET -> Root / "forever" => IO(
    Response[IO](headers = Headers("hello" -> "hi")).withEntity(Stream.constant("a"))
  )
  case r @ GET -> Root / "doubleRead" => (r.as[String], r.as[String])
    .flatMapN((a, b) => Ok(s"$a == $b"))
  case GET -> Root / "random" => Random.scalaUtilRandom[IO]
    .flatMap(_.nextInt)
    .flatMap(random => Ok(random.toString)) 
}

val okRequest = Request[IO](Method.GET, uri"/ok")
val badRequest = Request[IO](Method.GET, uri"/bad")
val postRequest = Request[IO](Method.POST, uri"/post")
val waitRequest = Request[IO](Method.GET, uri"/wait")
val boomRequest = Request[IO](Method.GET, uri"/boom")
val reverseRequest = Request[IO](Method.GET, uri"/reverse")
```
The provided examples will use the pattern `service(request)` to show the effects of the middleware, and sometimes
drain the response body with `response.as[Unit]`, this is necessary when the middleware depends on the request's
completion (like `MaxActiveRequests`) but in other cases we might choose to omit that. When testing middleware
in this manner (without running a client and a server) be aware of how it interacts with the response body and
consume it if necessary. 

Also note that these examples might use non-idiomatic constructs like `unsafeRunSync` and mutable collections for
conciseness.

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
cacheService(okRequest).unsafeRunSync().headers
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
dateService(okRequest).unsafeRunSync().headers
```

### HeaderEcho
Adds headers included in the request to the response.

```scala mdoc:silent
import org.http4s.server.middleware.HeaderEcho

val echoService = HeaderEcho.httpRoutes(echoHeadersWhen = _ => true)(service).orNotFound
```
```scala mdoc
echoService(okRequest.putHeaders("Hello" -> "hi")).unsafeRunSync().headers
```

### ResponseTiming

Sets response header with the request duration.

```scala mdoc:silent
import org.http4s.server.middleware.ResponseTiming

val timingService = ResponseTiming(service.orNotFound)
```
```scala mdoc
timingService(okRequest).unsafeRunSync().headers
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
val responseIO = requestIdService.orNotFound(okRequest)

// generated request id can be correlated with logs
val resp = responseIO.unsafeRunSync()
```
Note: `req.attributes.lookup(RequestId.requestIdAttrKey)` can also be used to lookup the request id
extracted from the header, or the generated request id.
```scala mdoc
resp.headers
resp.attributes.lookup(RequestId.requestIdAttrKey)
```

### StaticHeaders

Adds static headers to the response.

```scala mdoc:silent
import org.http4s.server.middleware.StaticHeaders

val staticHeadersService = StaticHeaders(Headers("X-Hello" -> "hi"))(service).orNotFound
```
```scala mdoc
staticHeadersService(okRequest).unsafeRunSync().headers
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
service.orNotFound(okRequest).unsafeRunSync().status
service.orNotFound(okWithSlash).unsafeRunSync().status

// with the middleware both work
autoSlashService(okRequest).unsafeRunSync().status
autoSlashService(okWithSlash).unsafeRunSync().status
```

### DefaultHead
Provides a naive implementation of a HEAD request for any GET routes. The response has the same headers
but no body. An attempt is made to interrupt the process of generating the body.

```scala mdoc:silent
import org.http4s.server.middleware.DefaultHead

val headService = DefaultHead(service).orNotFound
```
`/forever` has an infinite body but the `HEAD` request completes without body and includes the headers:
```scala mdoc
headService(Request[IO](Method.HEAD, uri"/forever"))
  .flatMap(r => 
    r.bodyText.compile.last.map(body => (body, r.status, r.headers))
  ).unsafeRunSync()
```

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
Redirects requests to https when the `X-Forwarded-Proto` header is `http`. This
header is usually provided by a load-balancer to indicate which protocol the client used.  

```scala mdoc:silent
import org.http4s.server.middleware.HttpsRedirect

val httpsRedirectService = HttpsRedirect(service).orNotFound
val httpRequest = okRequest
  .putHeaders("Host" -> "example.com", "X-Forwarded-Proto" -> "http")
```
```scala mdoc
httpsRedirectService(httpRequest).map(r => (r.headers, r.status)).unsafeRunSync()
```

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
val formRequest = Request[IO](Method.POST, uri"/queryForm")
  .withEntity(UrlForm.single("name", "John"))
```
Even though the `/queryForm` route takes query parameters, the form request works:
```scala mdoc
urlFormService(formRequest).flatMap(_.bodyText.compile.lastOrError).unsafeRunSync()
```

## Scaling and resource management

### ConcurrentRequests

React to requests being accepted and completed, could be used for metrics.

```scala mdoc:silent
import org.http4s.server.middleware.ConcurrentRequests
import org.http4s.server.{ContextMiddleware, HttpMiddleware}
import org.http4s.ContextRequest
import cats.data.Kleisli
import cats.effect.Ref

// a utility that drops the context from the request, since our service expects
// a plain request
def dropContext[A](middleware: ContextMiddleware[IO, A]): HttpMiddleware[IO] =
  routes => middleware(Kleisli((c: ContextRequest[IO, A]) => routes(c.req)))

val concurrentLog = Ref[IO].of(List.empty[String]).unsafeRunSync()

val concurrentService =
  ConcurrentRequests.route[IO](
      onIncrement = total => 
        concurrentLog.update(_ :+ s"someone comes to town, total=$total"),
      onDecrement = total => 
        concurrentLog.update(_ :+ s"someone leaves town, total=$total")
  ).map((middle: ContextMiddleware[IO, Long]) => 
    dropContext(middle)(service).orNotFound
  )

```
We drain the body (with `.as[Unit]`) so that we observe the request ending: 
```scala mdoc
concurrentService.flatMap(svc =>
  List.fill(3)(waitRequest).parTraverse(req => svc(req).flatMap(_.as[Unit]))
).void.unsafeRunSync()
concurrentLog.get.unsafeRunSync()
```

### EntityLimiter

Ensures the request body is under a specific length. It does so by inspecting
the body, not by simply checking `Content-Length` (which could be spoofed).
This could be useful for file uploads, or to prevent attacks that exploit 
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
  List.fill(5)(waitRequest).parTraverse(req => 
    service(req).flatMap(rsp => rsp.as[Unit].map(_ => rsp.status)).attempt
  )
).unsafeRunSync()
```

### Throttle
Reject requests that exceed a given rate. An in-memory implementation of a
[TokenBucket] - which refills at a given rate - is provided, but other strategies
can be used.
Like `MaxActiveRequest` this can be used prevent a service from being affect
by high load.

```scala mdoc:silent
import org.http4s.server.middleware.Throttle

// creating the middleware is effectful because of the default token bucket
val throttleService = Throttle.httpApp[IO](
  amount = 1,
  per = 10.milliseconds
)(service.orNotFound)
```
We'll submit request every 5ms and refill a token every 10ms:
```scala mdoc
throttleService.flatMap(service =>
  List.fill(5)(okRequest).traverse(req =>
    IO.sleep(5.millis) >>
      service(req).flatMap(rsp => rsp.as[Unit].map(_ => rsp.status)).attempt
  )
).unsafeRunSync()
```

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

Triggers an action if an error occurs while processing the request. Applies
to the error channel (like `IO.raiseError`, or `MonadThrow[F].raiseError`)
not http responses that indicate errors (like `BadRequest`). 
Could be used for logging and monitoring.

```scala mdoc:silent
import org.http4s.server.middleware.ErrorAction
import scala.collection.mutable.ArrayBuffer

val log = ArrayBuffer.empty[String] 
  
val errorActionService = ErrorAction.httpRoutes[IO](
  service, 
  (req, thr) => IO(log += ("Oops: " ++ thr.getMessage))
).orNotFound
```
```scala mdoc
errorActionService(boomRequest).attempt.void.unsafeRunSync()
log
```

### ErrorHandling

Interprets error conditions into an http response. This will interact with other
middleware that handles exceptions, like `ErrorAction`.
Different backends might handle exceptions differently, `ErrorAction` prevents
exceptions from reaching the backend and thus makes the service more backend-agnostic.

```scala mdoc:silent
import org.http4s.server.middleware.ErrorHandling
import scala.collection.mutable.ArrayBuffer

val errorHandlingService = ErrorHandling.httpRoutes[IO](service).orNotFound
```
For the first request (the service without `ErrorHandling`) we have to `.attempt`
to get a value that is renderable in this document, for the second request we get a response.  
```scala mdoc
service.orNotFound(boomRequest).attempt.unsafeRunSync()
errorHandlingService(boomRequest).map(_.status).unsafeRunSync()
```

### Metrics

Middleware to record service metrics. Requires an implementation of [MetricsOps] to receive metrics
data. Also provided are implementations for [Dropwizard](https://http4s.github.io/http4s-dropwizard-metrics/) 
and [Prometheus](https://http4s.github.io/http4s-prometheus-metrics/) metrics.

```scala mdoc:silent
import org.http4s.server.middleware.Metrics
import org.http4s.metrics.{MetricsOps, TerminationType}
import scala.collection.mutable.ArrayBuffer

val metricsLog = ArrayBuffer.empty[String]

val metricsOps = new MetricsOps[IO] {
  def increaseActiveRequests(classifier: Option[String]): IO[Unit] =
    IO(metricsLog += "increaseActiveRequests")
    
  def decreaseActiveRequests(classifier: Option[String]): IO[Unit] = IO.unit
  def recordHeadersTime(method: Method, elapsed: Long, classifier: Option[String]): IO[Unit] = 
    IO.unit
  def recordTotalTime(
    method: Method,
    status: Status,
    elapsed: Long,
    classifier: Option[String]
  ): IO[Unit] = IO.unit

  def recordAbnormalTermination(
    elapsed: Long,
    terminationType: TerminationType,
    classifier: Option[String]
  ): IO[Unit] = IO(metricsLog += ("abnormalTermination - " ++ terminationType.toString))
}

val metricsService = Metrics[IO](metricsOps)(service).orNotFound
```
```scala mdoc
metricsService(boomRequest).attempt.void.unsafeRunSync()
metricsService(okRequest).void.unsafeRunSync()
metricsLog
```

### RequestLogger, ResponseLogger, Logger

Log requests and responses. `ResponseLogger` logs the responses, `RequestLogger`
logs the request, `Logger` logs both.

```scala mdoc:silent
import org.http4s.server.middleware.Logger
import scala.collection.mutable.ArrayBuffer

val loggerLog = ArrayBuffer.empty[String]

val loggerService = Logger.httpRoutes[IO](
  logHeaders = false,
  logBody = true,
  redactHeadersWhen = _ => false,
  logAction = Some((msg: String) => IO(loggerLog += msg))
)(service).orNotFound

```
```scala mdoc
loggerService(reverseRequest.withEntity("mood")).attempt.void.unsafeRunSync()
loggerLog
```

## Advanced

### BodyCache

Consumes and caches a request body so that it can be reused later.
Usually reading the body twice is unsafe, this middleware ensures the body is always the same,
at the cost of keeping it in memory.

In this example we use a request body that always produces a different value once read:
```scala mdoc:silent
import org.http4s.server.middleware.BodyCache

val bodyCacheService = BodyCache.httpRoutes(service).orNotFound

val randomRequest = Request[IO](Method.GET, uri"/doubleRead")
  .withEntity(
    Stream.eval(
      Random.scalaUtilRandom[IO].flatMap(_.nextInt).map(random => random.toString)
    )
  )
```
`/doubleRead` reads the body twice, when using the middleware we see that both read values the same:
```scala mdoc
service.orNotFound(randomRequest).flatMap(r => r.as[String]).unsafeRunSync()
bodyCacheService(randomRequest).flatMap(r => r.as[String]).unsafeRunSync()
```

### BracketRequestResponse

Brackets the handling of the request ensuring an action happens before the service handles
the request (`acquire`) and another after the response is complete (`release`),
the result of `acquire` is threaded to the underlying service. 
It's used to implement `MaxActiveRequests` and `ConcurrentRequests`. 
See [BracketRequestResponse] for more constructors.

```scala mdoc:silent
import org.http4s.server.middleware.BracketRequestResponse
import org.http4s.ContextRoutes
import cats.effect.Ref

val ref = Ref[IO].of(0).unsafeRunSync()

val bracketMiddleware = BracketRequestResponse.bracketRequestResponseRoutes[IO, Int](
  acquire = ref.updateAndGet(_ + 1))(
  release = _ => ref.update(_ - 1)
)

val bracketService = bracketMiddleware(
  ContextRoutes.of[Int, IO] {
    case GET -> Root / "ok" as n => Ok(s"$n")
  }
).orNotFound

```
```scala mdoc
bracketService(okRequest).flatMap(r => r.as[String]).unsafeRunSync()
ref.get.unsafeRunSync()
```

### ChunkAggregator

Consumes and caches a response body so that it can be reused later.
Usually reading the body twice is unsafe, this middleware ensures the body is always the same,
at the cost of keeping it in memory.

Similarly to `BodyRequest` in this example we use a response body that always produces a different value:
```scala mdoc:silent
import org.http4s.server.middleware.ChunkAggregator

val chunkAggregatorService = ChunkAggregator.httpRoutes(service).orNotFound
```
```scala mdoc
chunkAggregatorService(Request[IO](Method.GET, uri"/random"))
  .flatMap(r => (r.as[String], r.as[String]).mapN((a, b) => s"$a == $b"))
  .unsafeRunSync()
```

### Jsonp

Jsonp is a javascript technique to load json data without using [XMLHttpRequest],
which bypasses the same-origin security policy implemented in browsers.

```scala mdoc:silent
// import org.http4s.server.middleware.Jsonp

// val jsonRoutes = HttpRoutes.of[IO] {
//  case GET -> Root / "json" => Ok("""{"a": 1}""")
// }

// val jsonService = Jsonp(callbackParam = "handleJson")(jsonRoutes).orNotFound
// val jsonRequest = Request[IO](Method.GET, uri"/json")
```
```scala mdoc
// jsonService(jsonRequest).flatMap(_.bodyText.compile.lastOrError).unsafeRunSync()
```

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

[Authentication]: auth.md
[CORS]: cors.md
[GZip]: gzip.md
[HSTS]: hsts.md
[CSRF]: hsts.md
[Caching]: @API_URL@org/http4s/server/middleware/Caching$.html
[TokenBucket]: @API_URL@org/http4s/server/middleware/Throttle$$TokenBucket.html
[MetricsOps]: @API_URL@org/http4s/server/middleware/Metrics$$MetricsOps.html
[BracketRequestResponse]: @API_URL@org/http4s/server/middleware/BracketRequestResponse$.html
[XMLHttpRequest]: https://developer.mozilla.org/en-US/docs/Web/API/XMLHttpRequest
