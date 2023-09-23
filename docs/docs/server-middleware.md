# Server Middleware

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
import org.http4s.client.Client
import cats.effect.unsafe.IORuntime
import scala.concurrent.duration._
import cats.effect.std.Random
import fs2.Stream
import cats.effect.std.Console

implicit val runtime: IORuntime = cats.effect.unsafe.IORuntime.global

object NameQueryParamMatcher extends QueryParamDecoderMatcher[String]("name")

val service = HttpRoutes.of[IO] {
  case GET -> Root / "bad" => BadRequest()
  case GET -> Root / "ok" => Ok()
  case r @ POST -> Root / "post" => r.as[Unit] >> Ok()
  case r @ POST -> Root / "echo" => r.as[String].flatMap(Ok(_))
  case GET -> Root / "b" / "c" => Ok()
  case POST -> Root / "queryForm" :? NameQueryParamMatcher(name) => Ok(s"hello $name")
  case GET -> Root / "wait" => IO.sleep(10.millis) >> Ok()
  case GET -> Root / "boom" => IO.raiseError(new RuntimeException("boom!"))
  case r @ POST -> Root / "reverse" => r.as[String].flatMap(s => Ok(s.reverse))
  case GET -> Root / "forever" => IO(
    Response[IO](headers = Headers("hello" -> "hi"))
      .withEntity(Stream.constant("a").covary[IO])
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
val reverseRequest = Request[IO](Method.POST, uri"/reverse")
val client = Client.fromHttpApp(service.orNotFound)
```

Also note that these examples might use non-idiomatic constructs like `unsafeRunSync` for
conciseness.


```scala mdoc:invisible
// we define our own Console[IO] to sidestep some mdoc issues: https://github.com/scalameta/mdoc/issues/517
import cats.Show
implicit val mdocConsoleIO: Console[IO] = new Console[IO] {
  val mdocConsoleOut = scala.Console.out
  def println[A](a: A)(implicit s: Show[A] = Show.fromToString[A]): IO[Unit] = {
    val str = s.show(a)
    IO.blocking(mdocConsoleOut.println(str)) 
  }

  def print[A](a: A)(implicit S: Show[A] = Show.fromToString[A]): IO[Unit] = IO.unit
  def error[A](a: A)(implicit S: Show[A] = Show.fromToString[A]): IO[Unit] = IO.unit
  def errorln[A](a: A)(implicit S: Show[A] = Show.fromToString[A]): IO[Unit] = IO.unit
  def readLineWithCharset(charset: java.nio.charset.Charset): IO[String] = IO.pure("")
}
```

@:navigationTree {
    entries = [ { target = "#" } ]
}

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

val cacheClient = Client.fromHttpApp(cacheService)
```
```scala mdoc
cacheClient.run(okRequest).use(_.headers.pure[IO]).unsafeRunSync()
cacheClient.run(badRequest).use(_.headers.pure[IO]).unsafeRunSync()
cacheClient.run(postRequest).use(_.headers.pure[IO]).unsafeRunSync()
```

### Date
Adds the current date to the response.

```scala mdoc:silent
import org.http4s.server.middleware.Date

val dateService = Date.httpRoutes(service).orNotFound
val dateClient = Client.fromHttpApp(dateService)
```
```scala mdoc
dateClient.run(okRequest).use(_.headers.pure[IO]).unsafeRunSync()
```

### HeaderEcho
Adds headers included in the request to the response.

```scala mdoc:silent
import org.http4s.server.middleware.HeaderEcho

val echoService = HeaderEcho.httpRoutes(echoHeadersWhen = _ => true)(service).orNotFound
val echoClient = Client.fromHttpApp(echoService)
```
```scala mdoc
echoClient.run(okRequest.putHeaders("Hello" -> "hi")).use(_.headers.pure[IO]).unsafeRunSync()
```

### ResponseTiming

Sets response header with the request duration.

```scala mdoc:silent
import org.http4s.server.middleware.ResponseTiming

val timingService = ResponseTiming(service.orNotFound)
val timingClient = Client.fromHttpApp(timingService)
```
```scala mdoc
timingClient.run(okRequest).use(_.headers.pure[IO]).unsafeRunSync()
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
    Console[IO].println(s"request received, cid=$reqId") *> Ok()
})

val requestIdClient = Client.fromHttpApp(requestIdService.orNotFound)
```
Note: `req.attributes.lookup(RequestId.requestIdAttrKey)` can also be used to lookup the request id
extracted from the header, or the generated request id.
```scala mdoc
requestIdClient.run(okRequest).use(resp =>
  (resp.headers, resp.attributes.lookup(RequestId.requestIdAttrKey)).pure[IO]
).unsafeRunSync()
```

### StaticHeaders

Adds static headers to the response.

```scala mdoc:silent
import org.http4s.server.middleware.StaticHeaders

val staticHeadersService = StaticHeaders(Headers("X-Hello" -> "hi"))(service).orNotFound
val staticHeaderClient = Client.fromHttpApp(staticHeadersService)
```
```scala mdoc
staticHeaderClient.run(okRequest).use(_.headers.pure[IO]).unsafeRunSync()
```

## Request rewriting

### AutoSlash

Removes a trailing slash from the requested url so that requests with trailing slash map to the route without.

```scala mdoc:silent
import org.http4s.server.middleware.AutoSlash

val autoSlashService = AutoSlash(service).orNotFound
val autoSlashClient = Client.fromHttpApp(autoSlashService)
val okWithSlash = Request[IO](Method.GET, uri"/ok/")
```
```scala mdoc
// without the middleware the request with trailing slash fails
client.status(okRequest).unsafeRunSync()
client.status(okWithSlash).unsafeRunSync()

// with the middleware both work
autoSlashClient.status(okRequest).unsafeRunSync()
autoSlashClient.status(okWithSlash).unsafeRunSync()
```

### DefaultHead
Provides a naive implementation of a HEAD request for any GET routes. The response has the same headers
but no body. An attempt is made to interrupt the process of generating the body.

```scala mdoc:silent
import org.http4s.server.middleware.DefaultHead

val headService = DefaultHead(service).orNotFound
val headClient = Client.fromHttpApp(headService)
```
`/forever` has an infinite body but the `HEAD` request terminates and includes the headers:
```scala mdoc
headClient.status(Request[IO](Method.HEAD, uri"/forever")).unsafeRunSync()
headClient.run(Request[IO](Method.HEAD, uri"/forever")).use(_.headers.pure[IO]).unsafeRunSync()
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
val overrideClient = Client.fromHttpApp(overrideService)
val overrideRequest = Request[IO](Method.GET, uri"/post?realMethod=POST")
```
```scala mdoc
client.status(overrideRequest).unsafeRunSync()
overrideClient.status(overrideRequest).unsafeRunSync()
```

### HttpsRedirect
Redirects requests to https when the `X-Forwarded-Proto` header is `http`. This
header is usually provided by a load-balancer to indicate which protocol the client used.

```scala mdoc:silent
import org.http4s.server.middleware.HttpsRedirect

val httpsRedirectService = HttpsRedirect(service).orNotFound
val httpsRedirectClient = Client.fromHttpApp(httpsRedirectService)
val httpRequest = okRequest
  .putHeaders("Host" -> "example.com", "X-Forwarded-Proto" -> "http")
```
```scala mdoc
httpsRedirectClient.run(httpRequest).use(r => (r.headers, r.status).pure[IO]).unsafeRunSync()
```

### TranslateUri
Removes a prefix from the path of the requested url.

```scala mdoc:silent
import org.http4s.server.middleware.TranslateUri

val translateService = TranslateUri(prefix = "a")(service).orNotFound
val translateRequest = Request[IO](Method.GET, uri"/a/b/c")
val translateClient = Client.fromHttpApp(translateService)
```
The following is successful even though `/b/c` is defined, and not `/a/b/c`:
```scala mdoc
translateClient.status(translateRequest).unsafeRunSync()
```

### UrlFormLifter

Transform `x-www-form-urlencoded` parameters into query parameters.

```scala mdoc:silent
import org.http4s.server.middleware.UrlFormLifter
import org.http4s.UrlForm

val urlFormService = UrlFormLifter.httpApp(service.orNotFound)
val urlFormClient = Client.fromHttpApp(urlFormService)

val formRequest = Request[IO](Method.POST, uri"/queryForm")
  .withEntity(UrlForm.single("name", "John"))
```
Even though the `/queryForm` route takes query parameters, the form request works:
```scala mdoc
urlFormClient.expect[String](formRequest).unsafeRunSync()
```

## Scaling and resource management

### ConcurrentRequests

React to requests being accepted and completed, could be used for metrics.

```scala mdoc:silent
import org.http4s.server.middleware.ConcurrentRequests
import org.http4s.server.{ContextMiddleware, HttpMiddleware}
import org.http4s.ContextRequest
import cats.data.Kleisli

// a utility that drops the context from the request, since our service expects
// a plain request
def dropContext[A](middleware: ContextMiddleware[IO, A]): HttpMiddleware[IO] =
  routes => middleware(Kleisli((c: ContextRequest[IO, A]) => routes(c.req)))

val concurrentService =
  ConcurrentRequests.route[IO](
      onIncrement = total => Console[IO].println(s"someone comes to town, total=$total"),
      onDecrement = total => Console[IO].println(s"someone leaves town, total=$total")
  ).map((middle: ContextMiddleware[IO, Long]) =>
    dropContext(middle)(service).orNotFound
  )

val concurrentClient = concurrentService.map(Client.fromHttpApp[IO])
```
```scala mdoc
concurrentClient.flatMap(cl =>
  List.fill(3)(waitRequest).parTraverse(req => cl.expect[Unit](req))
).void.unsafeRunSync()
```

### EntityLimiter

Ensures the request body is under a specific length. It does so by inspecting
the body, not by simply checking `Content-Length` (which could be spoofed).
This could be useful for file uploads, or to prevent attacks that exploit
a service that loads the whole body into memory. Note that many `EntityDecoder`s
are susceptible to this form of attack: the `String` entity decoder
will read the complete value into memory, while a json entity decoder might build
the full AST before attempting to decode. For this reason it's advisable to
apply this middleware unless something else, like a reverse proxy, is
applying this limit.

```scala mdoc:silent
import org.http4s.server.middleware.EntityLimiter

val limiterService = EntityLimiter.httpApp(service.orNotFound, limit = 16)
val limiterClient = Client.fromHttpApp(limiterService)
val smallRequest = postRequest.withEntity("*" * 15)
val bigRequest = postRequest.withEntity("*" * 16)
```
```scala mdoc
limiterClient.status(smallRequest).unsafeRunSync()
limiterClient.status(bigRequest).attempt.unsafeRunSync()
```

### MaxActiveRequests

Limit the number of active requests by rejecting requests over a certain limit.
This can be useful to ensure that your service remains responsive during high loads.

```scala mdoc:silent
import org.http4s.server.middleware.MaxActiveRequests

// creating the middleware is effectful
val maxService = MaxActiveRequests.forHttpApp[IO](maxActive = 2)
  .map(middleware => middleware(service.orNotFound))

val maxClient = maxService.map(Client.fromHttpApp[IO])
```
Some requests will fail if the limit is reached:
```scala mdoc
maxClient.flatMap(cl =>
  List.fill(5)(waitRequest).parTraverse(req => cl.status(req))
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

val throttleClient = throttleService.map(Client.fromHttpApp[IO])

```
We'll submit request every 5 ms and refill a token every 10 ms:
```scala mdoc
throttleClient.flatMap(cl =>
  List.fill(5)(okRequest).traverse(req => IO.sleep(5.millis) >> cl.status(req))
).unsafeRunSync()
```

### Timeout

Limits how long the underlying service takes to respond. The service is
cancelled, if there are uncancelable effects they are completed and only
then is the response returned.

```scala mdoc:silent
import org.http4s.server.middleware.Timeout

val timeoutService = Timeout.httpApp[IO](timeout = 5.milliseconds)(service.orNotFound)
val timeoutClient = Client.fromHttpApp(timeoutService)
```
`/wait` takes 10 ms to finish so it's cancelled:
```scala mdoc
timeoutClient.status(waitRequest).timed.unsafeRunSync()
```

## Error handling and Logging

### ErrorAction

Triggers an action if an error occurs while processing the request. Applies
to the error channel (like `IO.raiseError`, or `MonadThrow[F].raiseError`)
not http responses that indicate errors (like `BadRequest`).
Could be used for logging and monitoring.

```scala mdoc:silent
import org.http4s.server.middleware.ErrorAction

val errorActionService = ErrorAction.httpRoutes[IO](
  service,
  (req, thr) => Console[IO].println("Oops: " ++ thr.getMessage)
).orNotFound

val errorActionClient = Client.fromHttpApp(errorActionService)
```
```scala mdoc
errorActionClient.expect[Unit](boomRequest).attempt.unsafeRunSync()
```

### ErrorHandling

Interprets error conditions into an http response. This will interact with other
middleware that handles exceptions, like `ErrorAction`.
Different backends might handle exceptions differently, `ErrorAction` prevents
exceptions from reaching the backend and thus makes the service more backend-agnostic.

```scala mdoc:silent
import org.http4s.server.middleware.ErrorHandling

val errorHandlingService = ErrorHandling.httpRoutes[IO](service).orNotFound
val errorHandlingClient = Client.fromHttpApp(errorHandlingService)
```
For the first request (the service without `ErrorHandling`) we have to `.attempt`
to get a value that is renderable in this document, for the second request we get a response.
```scala mdoc
client.status(boomRequest).attempt.unsafeRunSync()
errorHandlingClient.status(boomRequest).unsafeRunSync()
```

### Metrics

Middleware to record service metrics. Requires an implementation of [MetricsOps] to receive metrics
data. Also provided are implementations for [Dropwizard](https://http4s.github.io/http4s-dropwizard-metrics/)
and [Prometheus](https://http4s.github.io/http4s-prometheus-metrics/) metrics.

```scala mdoc:silent
import org.http4s.server.middleware.Metrics
import org.http4s.metrics.{MetricsOps, TerminationType}

val metricsOps = new MetricsOps[IO] {
  def increaseActiveRequests(classifier: Option[String]): IO[Unit] =
    Console[IO].println("increaseActiveRequests")

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
  ): IO[Unit] = Console[IO].println(s"abnormalTermination - $terminationType")
}

val metricsService = Metrics[IO](metricsOps)(service).orNotFound
val metricsClient = Client.fromHttpApp(metricsService)
```
```scala mdoc
metricsClient.expect[Unit](boomRequest).attempt.void.unsafeRunSync()
metricsClient.expect[Unit](okRequest).unsafeRunSync()
```

### RequestLogger, ResponseLogger, Logger

Log requests and responses. `ResponseLogger` logs the responses, `RequestLogger`
logs the request, `Logger` logs both.

```scala mdoc:silent
import org.http4s.server.middleware.Logger

val loggerService = Logger.httpRoutes[IO](
  logHeaders = false,
  logBody = true,
  redactHeadersWhen = _ => false,
  logAction = Some((msg: String) => Console[IO].println(msg))
)(service).orNotFound

val loggerClient = Client.fromHttpApp(loggerService)
```
```scala mdoc
loggerClient.expect[Unit](reverseRequest.withEntity("mood")).unsafeRunSync()
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

val bodyCacheClient = Client.fromHttpApp(bodyCacheService)

```
`/doubleRead` reads the body twice, when using the middleware we see that both read values the same:
```scala mdoc
client.expect[String](randomRequest).unsafeRunSync()
bodyCacheClient.expect[String](randomRequest).unsafeRunSync()
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

val bracketClient = Client.fromHttpApp(bracketService)

```
```scala mdoc
bracketClient.expect[String](okRequest).unsafeRunSync()
ref.get.unsafeRunSync()
```

### ChunkAggregator

Consumes and caches a response body so that it can be reused later.
Usually reading the body twice is unsafe, this middleware ensures the body is always the same,
at the cost of keeping it in memory.

Similarly to `BodyRequest` in this example we use a response body that always produces a different value:
```scala mdoc:silent
import org.http4s.server.middleware.ChunkAggregator

def doubleBodyMiddleware(service: HttpRoutes[IO]): HttpRoutes[IO] = Kleisli { (req: Request[IO]) =>
  service(req).map {
    case Status.Successful(resp) =>
      resp.withBodyStream(resp.body ++ resp.body)
    case resp => resp
  }
}

val chunkAggregatorService = doubleBodyMiddleware(ChunkAggregator.httpRoutes(service)).orNotFound
val chunkAggregatorClient = Client.fromHttpApp(chunkAggregatorService)
```
```scala mdoc
chunkAggregatorClient
  .expect[String](Request[IO](Method.POST, uri"/echo").withEntity("foo"))
  .map(e => s"$e == foofoo")
  .unsafeRunSync()
```

### Jsonp

Jsonp is a javascript technique to load json data without using [XMLHttpRequest],
which bypasses the same-origin security policy implemented in browsers.
Jsonp usage is discouraged and can often be replaced with correct CORS configuration.
This middleware has been deprecated as of 0.23.24.

```scala mdoc:compile-only
import org.http4s.server.middleware.Jsonp

val jsonRoutes = HttpRoutes.of[IO] {
  case GET -> Root / "json" => Ok("""{"a": 1}""")
}

val jsonService = Jsonp(callbackParam = "handleJson")(jsonRoutes).orNotFound
val jsonClient = Client.fromHttpApp(jsonService)
val jsonRequest = Request[IO](Method.GET, uri"/json")

jsonClient.expect[String](jsonRequest).unsafeRunSync()
```

### ContextMiddleware

This middleware allows extracting context from a request and propagating it down to the routes.

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
val contextClient = Client.fromHttpApp(contextService)
val contextRequest = Request[IO](Method.GET, uri"/ok").putHeaders(UserId("Jack"))
```
```scala mdoc
contextClient.expect[String](contextRequest).unsafeRunSync()
```

[Authentication]: auth.md
[CORS]: cors.md
[GZip]: gzip.md
[HSTS]: hsts.md
[CSRF]: csrf.md
[Caching]: @API_URL@org/http4s/server/middleware/Caching$.html
[TokenBucket]: @API_URL@org/http4s/server/middleware/Throttle$$TokenBucket.html
[MetricsOps]: @API_URL@org/http4s/server/middleware/Metrics$$MetricsOps.html
[BracketRequestResponse]: @API_URL@org/http4s/server/middleware/BracketRequestResponse$.html
[XMLHttpRequest]: https://developer.mozilla.org/en-US/docs/Web/API/XMLHttpRequest
