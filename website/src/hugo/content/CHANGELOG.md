# v0.18.0-SNAPSHOT
* Use http4s-dsl with any effect type by either:
    * extend `Http4sDsl[F]`
    * create an object that extends `Http4sDsl[F]`, and extend that.
    * `import org.http4s.dsl.io._` is still available for those who
      wish to specialize on `cats.effect.IO`
* Remove `Semigroup[F[MaybeResponse[F]]]` constraint from
  `BlazeBuilder`.
* Fix `AutoSlash` middleware when a service is mounted with a prefix.
* Publish internal http4s-parboiled2 as a separate module.  This does
  not add any new third-party dependencies, but unbreaks `sbt
  publishLocal`.
* Add `Request.from`, which respects `X-Fowarded-For` header.
* Make `F` in `EffectMessageSyntax` invariant
* Upgraded dependencies:
    * jawn-fs2-0.12.0-M2
    * twirl-1.3.7

# v0.17.0 (2017-09-01)
* Honor `Retry-After` header in `Retry` middleware.  The response will
  not be retried until the maximum of the backoff strategy and any
  time specified by the `Retry-After` header of the response.
* The `RetryPolicy.defaultRetriable` only works for methods guaranteed
  to not have a body.  In fs2, we can't introspect the stream to
  guarantee that it can be rerun.  To retry requests for idempotent
  request methods, use `RetryPolicy.unsafeRetriable`.  To retry
  requests regardless of method, use
  `RetryPolicy.recklesslyRetriable`.
* Fix `Logger` middleware to render JSON bodies as text, not as a hex
  dump.
* `MultipartParser.parse` returns a stream of `ByteVector` instead of
  a stream of `Byte`. This perserves chunking when parsing into the
  high-level `EntityDecoder[Multipart]`, and substantially improves
  performance on large files.  The high-level API is not affected.

# v0.16.0 (2017-09-01)
* `Retry` middleware takes a `RetryPolicy` instead of a backoff
  strategy.  A `RetryPolicy` is a function of the request, the
  response, and the number of attempts.  Wrap the previous `backoff`
  in `RetryPolicy {}` for compatible behavior.
* Expose a `Part.fileData` constructor that accepts an `EntityBody`.

# v0.17.0-RC3 (2017-08-29)
* In blaze-server, when doing chunked transfer encoding, flush the
  header as soon as it is available.  It previously buffered until the
  first chunk was available.

# v0.16.0-RC3 (2017-08-29)
* Add a `responseHeaderTimeout` property to `BlazeClientConfig`.  This
  measures the time between the completion of writing the request body
  to the reception of a complete response header.
* Upgraded dependencies:
    * async-http-client-2.0.35

# v0.18.0-M1 (2107-08-24)

This release is the product of a long period of parallel development
across different foundation libraries, making a detailed changelog
difficult.  This is a living document, so if any important points are
missed here, please send a PR.

The most important change in http4s-0.18 is that the effect type is
parameterized.  Where previous versions were specialized on
`scalaz.concurrent.Task` or `fs2.Task`, this version supports anything
with a `cats.effect.Effect` instance.  The easiest way to port an
existing service is to replace your `Task` with `cats.effect.IO`,
which has a similar API and is already available on your classpath.
If you prefer to bring your own effect, such as `monix.eval.Task` or
stick to `scalaz.concurrent.Task` or put a transformer on `IO`, that's
fine, too!

The parameterization chanages many core signatures throughout http4s:
- `Request` and `Response` become `Request[F[_]]` and
  `Response[F[_]]`.  The `F` is the effect type of the body (i.e.,
  `Stream[F, Byte]`), or what the body `.run`s to.
- `HttpService` becomes `HttpService[F[_]]`, so that the service
  returns an `F[Response[F]]`.  Instead of constructing with
  `HttpService { ... }`, we now declare the effect type of the
  service, like `HttpService[IO] { ... }`.  This determines the type
  of request and response handled by the service.
- `EntityEncoder[A]` and `EntityDecoder[A]` are now
  `EntityEncoder[F[_], A]` and `EntityDecoder[F[_], A]`, respectively.
  These act as a codec for `Request[F]` and `Response[F]`.  In practice,
  this change tends to be transparent in the DSL.
- The server builders now take an `F` parameter, which needs to match
  the services mounted to them.
- The client now takes an `F` parameter, which determines the requests
  and responses it handles.

Several dependencies are upgraded:
- cats-1.0.0.MF
- circe-0.9.0-M1
- fs2-0.10.0-M6
- fs2-reactive-streams-0.2.2
- jawn-fs2-0.12.0-M1

# v0.17.0-RC2 (2017-08-24)
* Remove `ServiceSyntax.orNotFound(a: A): Task[Response]` in favor of
  `ServiceSyntax.orNotFound: Service[Request, Response]`

# v0.16.0-RC2 (2017-08-24)
* Move http4s-blaze-core from `org.http4s.blaze` to
  `org.http4s.blazecore` to avoid a conflict with the non-http4s
  blaze-core module.
* Change `ServiceOps` to operate on a `Service[?, MaybeResponse]`.
  Give it an `orNotFound` that returns a `Service`.  The
  `orNotFound(a: A)` overload is left for compatibility with Scala
  2.10.
* Build with Lightbend compiler instead of Typelevel compiler so we
  don't expose `org.typelevel` dependencies that are incompatible with
  ntheir counterparts in `org.scala-lang`.
* Upgraded dependencies:
    * blaze-0.12.7 (fixes eviction notice in http4s-websocket)
    * twirl-1.3.4

# v0.17.0-RC1 (2017-08-16)
* Port `ChunkAggregator` to fs2
* Add logging middleware
* Standardize on `ExecutionContext` over `Strategy` and `ExecutorService`
* Implement `Age` header
* Fix `Client#toHttpService` to not dispose until the body is consumed
* Add a buffered implementation of `EntityDecoder[Multipart]`
* In async-http-client, don't use `ReactiveStreamsBodyGenerator` unless there is
  a body to transmit. This fixes an `IllegalStateException: unexpected message
  type`
* Add `HSTS` middleware
* Add option to serve pre-gzipped resources
* Add RequestLogging and ResponseLogging middlewares
* `StaticFile` options return `OptionT[Task, ?]`
* Set `Content-Length` or `Transfer-Encoding: chunked` header when serving
  from a URL
* Explicitly close `URLConnection``s if we are not reading the contents
* Upgrade to:
    * async-http-client-2.0.34
    * fs2-0.9.7
    * metrics-core-3.2.4
    * scodec-bits-1.1.5

# v0.16.0-RC1 (2017-08-16)
* Remove laziness from `ArbitraryInstances`
* Support an arbitrary predicate for CORS allowed origins
* Support `Access-Control-Expose-Headers` header for CORS
* Fix thread safety issue in `EntityDecoder[XML]`
* Support IPV6 headers in `X-Forwarded-For`
* Add `status` and `successful` methods to client
* Overload `client.fetchAs` and `client.streaming` to accept a `Task[Request]`
* Replace `Instant` with `HttpDate` to avoid silent truncation and constrain
  to dates that are legally renderable in HTTP.
* Fix bug in hash code of `CaseInsensitiveString`
* Update `request.pathInfo` when changing `request.withUri`. To keep these
  values in sync, `request.copy` has been deprecated, but copy constructors
  based on `with` have been added.
* Remove `name` from `AttributeKey`.
* Add `withFragment` and `withoutFragment` to `Uri`
* Construct `Content-Length` with `fromLong` to ensure validity, and
  `unsafeFromLong` when you can assert that it's positive.
* Add missing instances to `QueryParamDecoder` and `QueryParamEncoder`.
* Add `response.cookies` method to get a list of cookies from `Set-Cookie`
  header.  `Set-Cookie` is no longer a `Header.Extractable`, as it does
  not adhere to the HTTP spec of being concatenable by commas without
  changing semantics.
* Make servlet `HttpSession` available as a request attribute in servlet
  backends
* Fix `Part.name` to return the name from the `Content-Disposition` header
  instead of the name _of_ the `Content-Disposition` header. Accordingly, it is
  no longer a `CaseInsensitiveString`
* `Request.toString` and `Response.toString` now redact sensitive headers. A
  method to redact arbitrary headers is added to `Headers`.
* `Retry-After` is now modeled as a `Either[HttpDate, Long]` to reflect either
  an http-date or delta-seconds value.
* Look for index.html in `StaticFile` when rendering a directory instead of
  returning `401 Unauthorized`.
* Limit dates to a minimum of January 1, 1900, per RFC.
* Add `serviceErrorHandler` to `ServerBuilder` to allow pluggable error handlers
  when a server backend receives a failed task or a thrown Exception when
  invoking a service. The default calls `toHttpResponse` on `MessageFailure` and
  closes the connection with a `500 InternalServerError` on other non-fatal
  errors.  Fatal errors are left to the server.
* `FollowRedirect` does not propagate sensitive headers when redirecting to a
  different authority.
* Add Content-Length header to empty response generators
* Upgraded dependencies:
    * async-http-client-2.0.34
    * http4s-websocket-0.2.0
    * jetty-9.4.6.v20170531
    * json4s-3.5.3
    * log4s-1.3.6
    * metrics-core-3.2.3
    * scala-2.12.3-bin-typelevel-4
    * scalaz-7.2.15
    * tomcat-8.5.20

# v0.15.16 (2017-07-20)
* Backport rendering of details in `ParseFailure.getMessage`

# ~~v0.15.15 (2017-07-20)~~
* Oops. Same as v0.15.14.

# v0.15.14 (2017-07-10)
* Close parens in `Request.toString`
* Use "message" instead of "request" in message body failure messages
* Add `problem+json` media type
* Tolerate `[` and `]` in queries parsing URIs. These characters are parsed, but
  percent-encoded.

# v0.17.0-M3 (2017-05-27)
* Fix file corruption issue when serving static files from the classpath

# v0.16.0-M3 (2017-05-25)
* Fix `WebjarService` so it matches assets.
* `ServerApp` overrides `process` to leave a single abstract method
* Add gzip trailer in `GZip` middleware
* Upgraded dependencies:
    * circe-0.8.0
    * jetty-9.4.5.v20170502
    * scalaz-7.2.13
    * tomcat-8.5.15
* `ProcessApp` uses a `Process[Task, Nothing]` rather than a
  `Process[Task, Unit]`
* `Credentials` is split into `Credentials.AuthParams` for key-value pairs and
  `Credentials.Token` for legacy token-based schemes.  `OAuthBearerToken` is
  subsumed by `Credentials.Token`.  `BasicCredentials` no longer extends
  `Credentials`, but is extractable from one.  This model permits the
  definition of other arbitrary credential schemes.
* Add `fromSeq` constructor to `UrlForm`
* Allow `WebjarService` to pass on methods other than `GET`.  It previously
  threw a `MatchError`.

# v0.15.13 (2017-05-25)
* Patch-level upgrades to dependencies:
    * async-http-client-2.0.32
    * blaze-0.12.6 (fixes infinite loop in some SSL handshakes)
    * jetty-9.3.19.v20170502
    * json4s-3.5.2
    * tomcat-8.0.44

# v0.15.12 (2017-05-11)
* Fix GZip middleware to render a correct stream

# v0.17.0-M2 (2017-04-30)
* `Timeout` middleware takes an implicit `Scheduler` and
  `ExecutionContext`
* Bring back `http4s-async-client`, based on `fs2-reactive-stream`
* Restore support for WebSockets

# v0.16.0-M2 (2017-04-30)
* Upgraded dependencies:
    * argonaut-6.2
    * jetty-9.4.4.v20170414
    * tomcat-8.5.14
* Fix `ProcessApp` to terminate on process errors
* Set `secure` request attribute correctly in blaze server
* Exit with code `-1` when `ProcessApp` fails
* Make `ResourceService` respect `If-Modified-Since`
* Rename `ProcessApp.main` to `ProcessApp.process` to avoid overload confusio
* Avoid intermediate String allocation in Circe's `jsonEncoder`
* Adaptive EntityDecoder[Json] for circe: works directly from a ByteBuffer for
  small bodies, and incrementally through jawn for larger.
* Capture more context in detail message of parse errors

# v0.15.11 (2017-04-29)
* Upgrade to blaze-0.12.5 to pick up fix for `StackOverflowError` in
  SSL handshake

# v0.15.10 (2017-04-28)
* Patch-level upgrades to dependencies
* argonaut-6.2
* scalaz-7.2.12
* Allow preambles and epilogues in multipart bodies
* Limit multipart headers to 40 kilobytes to avoid unbounded buffering
  of long lines in a header
* Remove `' '` and `'?'` from alphabet for generated multipart
  boundaries, as these are not token characters and are known to cause
  trouble for some multipart implementations
* Fix multipart parsing for unlucky input chunk sizes

# v0.15.9 (2017-04-19)
* Terminate `ServerApp` even if the server fails to start
* Make `ResourceService` respect `If-Modified-Since`
* Patch-level upgrades to dependencies:
* async-http-client-2.0.31
* jetty-9.3.18.v20170406
* json4s-3.5.1
* log4s-1.3.4
* metrics-core-3.1.4
* scalacheck-1.13.5
* scalaz-7.1.13 or scalaz-7.2.11
* tomcat-8.0.43

# v0.17.0-M1 (2017-04-08)
* First release on cats and fs2
    * All scalaz types and typeclasses replaced by cats equivalengts
	* `scalaz.concurrent.Task` replaced by `fs2.Task`	
	* `scalaz.stream.Process` replaced by `fs2.Stream`
* Roughly at feature parity with v0.16.0-M1. Notable exceptions:
	* Multipart not yet supported
	* Web sockets not yet supported
	* Client retry middleware can't check idempotence of requests
	* Utilties in `org.http4s.util.io` not yet ported

# v0.16.0-M1 (2017-04-08)
* Fix type of `AuthedService.empty`
* Eliminate `Fallthrough` typeclass.  An `HttpService` now returns
  `MaybeResponse`, which can be a `Response` or `Pass`.  There is a
  `Semigroup[MaybeResponse]` instance that allows `HttpService`s to be
  chained as a semigroup.  `service orElse anotherService` is
  deprecated in favor of `service |+| anotherService`.
* Support configuring blaze and Jetty servers with a custom
  `SSLContext`.
* Upgraded dependencies for various modules:
    * async-http-client-2.0.31
    * circe-0.7.1
    * jetty-9.4.3.v20170317
    * json4s-3.5.1
    * logback-1.2.1
    * log4s-1.3.4
    * metrics-3.2.0
    * scalacheck-1.13.5
    * tomcat-8.0.43
* Deprecate `EntityEncoder[ByteBuffer]` and
  `EntityEncoder[CharBuffer]`.
* Add `EntityDecoder[Unit]`.
* Move `ResponseClass`es into `Status`.
* Use `SSLContext.getDefault` by default in blaze-client.  Use
  `BlazeServerConfig.insecure` to ignore certificate validity.  But
  please don't.
* Move `CaseInsensitiveString` syntax to `org.http4s.syntax`.
* Bundle an internal version of parboiled2.  This decouples core from
  shapeless, allowing applications to use their preferred version of
  shapeless.
* Rename `endpointAuthentication` to `checkEndpointAuthentication`.
* Add a `WebjarService` for serving files out of web jars.
* Implement `Retry-After` header.
* Stop building with `delambdafy` on Scala 2.11.
* Eliminate finalizer on `BlazeConnection`.
* Respond OK to CORS pre-flight requests even if the wrapped service
  does not return a successful response.  This is to allow `CORS`
  pre-flight checks of authenticated services.
* Deprecate `ServerApp` in favor of `org.http4s.util.ProcessApp`.  A
  `ProcessApp` is easier to compose all the resources a server needs via
  `Process.bracket`.
* Implement a `Referer` header.

# v0.15.8 (2017-04-06)
* Cache charset lookups to avoid synchronization.  Resolution of
  charsets is synchronized, with a cache size of two.  This avoids
  the synchronized call on the HTTP pool.
* Strip fragment from request target in blaze-client.  An HTTP request
  target should not include the fragment, and certain servers respond
  with a `400 Bad Request` in their presence.

# v0.15.7 (2017-03-09)
* Change default server and client executors to a minimum of four
  threads.
* Bring scofflaw async-http-client to justice for its brazen
  violations of Reactive Streams Rule 3.16, requesting of a null
  subscription.
* Destroy Tomcat instances after stopping, so they don't hold the port
* Deprecate `ArbitraryInstances.genCharsetRangeNoQuality`, which can
  cause deadlocks
* Patch-level upgrades to dependencies:
    * async-http-client-2.0.30
    * jetty-9.3.16.v20170120
    * logback-1.1.11
    * metrics-3.1.3
    * scala-xml-1.0.6
    * scalaz-7.2.9
    * tomcat-8.0.41
    * twirl-1.2.1

# v0.15.6 (2017-03-03)
* Log unhandled MessageFailures to `org.http4s.server.message-failures`

# v0.15.5 (2017-02-20)
* Allow services wrapped in CORS middleware to fall through
* Don't log message about invalid CORS headers when no `Origin` header present
* Soften log about invalid CORS headers from info to debug

# v0.15.4 (2017-02-12)
* Call `toHttpResponse` on tasks failed with `MessageFailure`s from
  `HttpService`, to get proper 4xx handling instead of an internal
  server error.

# v0.15.3 (2017-01-17)
* Dispose of redirect responses in `FollowRedirect`. Fixes client deadlock under heavy load
* Refrain from logging headers with potentially sensitive info in blaze-client
* Add `hashCode` and `equals` to `Headers`
* Make `challenge` in auth middlewares public to facilitate composing multiple auth mechanisms
* Fix blaze-client detection of stale connections

# v0.15.2 (2016-12-29)
* Add helpers to add cookies to requests

# v0.12.6 (2016-12-29)
* Backport rendering of details in `ParseFailure.getMessage`

# ~~v0.12.5 (2016-12-29)~~
* ~~Backport rendering of details in `ParseFailure.getMessage`~~ Oops.

# v0.15.1 (2016-12-20)
* Fix GZip middleware to fallthrough non-matching responses
* Fix UnsupportedOperationException in Arbitrary[Uri]
* Upgrade to Scala 2.12.1 and Scalaz 7.2.8

# v0.15.0 (2016-11-30)
* Add support for Scala 2.12
* Added `Client.fromHttpService` to assist with testing.
* Make all case classes final where possible, sealed where not.
* Codec for Server Sent Events (SSE)
* Added JSONP middleware
* Improve Expires header to more easily build the header and support parsing of the header
* Replce lazy `Raw.parsed` field with a simple null check
* Added support for Zipkin headers
* Eliminate response attribute for detecting fallthrough response.
  The fallthrough response must be `Response.fallthrough`.
* Encode URI path segments created with `/`
* Introduce `AuthedRequest` and `AuthedService` types.
* Replace `CharSequenceEncoder` with `CharBufferEncoder`, assuming
  that `CharBuffer` and `String` are the only `CharSequence`s one
  would want to encode.
* Remove `EnittyEncoder[Char]` and `EntityEncoder[Byte]`.  Send an
  array, buffer, or String if you want this.
* Add `DefaultHead` middleware for `HEAD` implementation.
* Decouple `http4s-server` from Dropwizard Metrics.  Metrics code is
  in the new `http4s-metrics` module.
* Allow custom scheduler for timeout middleware.
* Add parametric empty `EntityEncoder` and `EntityEncoder[Unit]`.
* Replace unlawful `Order[CharsetRange]` with `Equal[CharsetRange]`.
* Auth middlewares renamed `BasicAuth` and `DigestAuth`.
* `BasicAuth` passes client password to store instead of requesting
  password from store.
* Remove realm as an argument to the basic and digest auth stores.
* Basic and digest auth stores return a parameterized type instead of
  just a String username.
* Upgrade to argonaut-6.2-RC2, circe-0.6.1, json4s-3.5.0

# v0.14.11 (2016-10-25)
* Fix expansion of `uri` and `q` macros by qualifying with `_root_`

# v0.14.10 (2016-10-12)
* Include timeout type and duration in blaze client timeouts

# v0.14.9 (2016-10-09)
* Don't use `"null"` as query string in servlet backends for requests without a query string

# v0.14.8 (2016-10-04)
* Allow param names in UriTemplate to have encoded, reserved parameters
* Upgrade to blaze-0.12.1, to fix OutOfMemoryError with direct buffers
* Upgrade to Scalaz 7.1.10/7.2.6
* Upgrade to Jetty 9.3.12
* Upgrade to Tomcat 8.0.37

# v0.14.7 (2016-09-25)
* Retry middleware now only retries requests with idempotent methods
  and pure bodies and appropriate status codes
* Fix bug where redirects followed when an effectful chunk (i.e., `Await`) follows pure ones.
* Don't uppercase two hex digits after "%25" when percent encoding.
* Tolerate invalid percent-encodings when decoding.
* Omit scoverage dependencies from POM

# v0.14.6 (2016-09-11)
* Don't treat `Kill`ed responses (i.e., HEAD requests) as abnormal
  termination in metrics

# v0.14.5 (2016-09-02)
* Fix blaze-client handling of HEAD requests

# v0.14.4 (2016-08-29)
* Don't render trailing "/" for URIs with empty paths
* Avoid calling tail of empty list in `/:` extractor

# v0.14.3 (2016-08-24)
* Follow 301 and 302 responses to POST with a GET request.
* Follow all redirect responses to HEAD with a HEAD request.
* Fix bug where redirect response is disposed prematurely even if not followed.
* Fix bug where payload headers are sent from original request when
  following a redirect with a GET or HEAD.
* Return a failed task instead of throwing when a client callback
  throws an exception. Fixes a resource leak.
* Always render `Date` header in GMT.
* Fully support the three date formats specified by RFC 7231.
* Always specify peer information in blaze-client SSL engines
* Patch upgrades to latest async-http-client, jetty, scalaz, and scalaz-stream

# v0.14.2 (2016-08-10)
* Override `getMessage` in `UnexpectedStatus`

# v0.14.1 (2016-06-15)
* Added the possibility to specify custom responses to MessageFailures
* Address issue with Retry middleware leaking connections
* Fixed the status code for a semantically invalid request to `422 UnprocessableEntity`
* Rename `json` to `jsonDecoder` to reduce possibility of implicit shadowing
* Introduce the `ServerApp` trait
* Deprectate `onShutdown` and `awaitShutdown` in `Server`
* Support for multipart messages
* The Path extractor for Long now supports negative numbers
* Upgrade to scalaz-stream-0.8.2(a) for compatibility with scodec-bits-1.1
* Downgrade to argonaut-6.1 (latest stable release) now that it cross builds for scalaz-7.2
* Upgrade parboiled2 for compatibility with shapeless-2.3.x

# ~~v0.14.0 (2016-06-15)~~
* Recalled. Use v0.14.1 instead.

# v0.13.3 (2016-06-15)
* Address issue with Retry middleware leaking connections.
* Pass the reason string when setting the `Status` for a successful `ParseResult`.

# v0.13.2 (2016-04-13)
* Fixes the CanBuildFrom for RequestCookieJar to avoid duplicates.
* Update version of jawn-parser which contains a fix for Json decoding.

# v0.13.1 (2016-04-07)
* Remove implicit resolution of `DefaultExecutor` in blaze-client.

# v0.13.0 (2016-03-29)
* Add support for scalaz-7.2.x (use version 0.13.0a).
* Add a client backed based on async-http-client.
* Encode keys when rendering a query string.
* New entity decoder based on json4s' extract.
* Content-Length now accepts a Long.
* Upgrade to circe-0.3, json4s-3.3, and other patch releases.
* Fix deadlocks in blaze resulting from default executor on single-CPU machines.
* Refactor `DecodeFailure` into a new `RequestFailure` hierarchy.
* New methods for manipulating `UrlForm`.
* All parsed headers get a `parse` method to construct them from their value.
* Improve error message for unsupported media type decoding error.
* Introduce `BlazeClientConfig` class to simplify client construction.
* Unify client executor service semantics between blaze-client and async-http-client.
* Update default response message for UnsupportedMediaType failures.
* Add a `lenient` flag to blazee configuration to accept illegal characters in headers.
* Remove q-value from `MediaRange` and `MediaType`, replaced by `MediaRangeAndQValue`.
* Add `address` to `Server` trait.
* Lazily construct request body in Servlet NIO to support HTTP 100.
* Common operations pushed down to `MessageOps`.
* Fix loop in blaze-client when no connection can be established.
* Privatize most of the blaze internal types.
* Enable configuration of blaze server parser lengths.
* Add trailer support in blaze client.
* Provide an optional external executor to blaze clients.
* Fix Argonaut string interpolation

# v0.12.4 (2016-03-10)
* Fix bug on rejection of invalid URIs.
* Do not send `Transfer-Encoding` or `Content-Length` headers for 304 and others.
* Don't quote cookie values.

# v0.12.3 (2016-02-24)
* Upgrade to jawn-0.8.4 to fix decoding escaped characters in JSON.

# v0.12.2 (2016-02-22)
* ~~Upgrade to jawn-0.8.4 to fix decoding escaped characters in JSON.~~ Oops.

# v0.12.1 (2016-01-30)
* Encode keys as well as values when rendering a query.
* Don't encode '?' or '/' when encoding a query.

# v0.12.0 (2016-01-15)
* Refactor the client API for resource safety when not reading the entire body.
* Rewrite client connection pool to support maximum concurrent
  connections instead of maximum idle connections.
* Optimize body collection for better connection keep-alive rate.
* Move `Service` and `HttpService`, because a `Client` can be viewed as a `Service`.
* Remove custom `DateTime` in favor of `java.time.Instant`.
* Support status 451 Unavailable For Legal Reasons.
* Various blaze-client optimizations.
* Don't let Blaze `IdentityWriter` write more than Content-Length bytes.
* Remove `identity` `Transfer-Encoding`, which was removed in HTTP RFC errata.
* In blaze, `requireClose` is now the return value of `writeEnd`.
* Remove body from `Request.toString` and `Response.toString`.
* Move blaze parser into its own class.
* Trigger a disconnect if an ignored body is too long.
* Configurable thread factories for happier profiling.
* Fix possible deadlock in default client execution context.

# v0.11.3 (2015-12-28)
* Blaze upgrade to fix parsing HTTP responses without a reason phrase.
* Don't write more than Content-Length bytes in blaze.
* Fix infinite loop in non-blocking Servlet I/O.
* Never write a response body on HEAD requests to blaze.
* Add missing `'&'` between multivalued k/v pairs in `UrlFormCodec.encode`

# v0.11.2 (2015-12-04)
* Fix stack safety issue in async servlet I/O.
* Reduce noise from timeout exceptions in `ClientTimeoutStage`.
* Address file descriptor leaks in blaze-client.
* Fix `FollowRedirect` middleware for 303 responses.
* Support keep-alives for client requests with bodies.

# v0.11.1 (2015-11-29)
* Honor `connectorPoolSize` and `bufferSize` parameters in `BlazeBuilder`.
* Add convenient `ETag` header constructor.
* Wait for final chunk to be written before closing the async context in non-blocking servlet I/O.
* Upgrade to jawn-streamz-0.7.0 to use scalaz-stream-0.8 across the board.

# v0.11.0 (2015-11-20)
* Upgrade to scalaz-stream 0.8
* Add Circe JSON support module.
* Add ability to require content-type matching with EntityDecoders.
* Cleanup blaze-client internals.
* Handle empty static files.
* Add ability to disable endpoint authentication for the blaze client.
* Add charset encoding for Argonaut JSON EntityEncoder.

# v0.10.1 (2015-10-07)
* Processes render data in chunked encoding by default.
* Incorporate type name into error message of QueryParam.
* Comma separate Access-Control-Allow-Methods header values.
* Default FallThrough behavior inspects for the FallThrough.fallthroughKey.

# v0.10.0 (2015-09-03)
* Replace `PartialService` with the `Fallthrough` typeclass and `orElse` syntax.
* Rename `withHeaders` to `replaceAllHeaders`
* Set https endpoint identification algorithm when possible.
* Stack-safe `ProcessWriter` in blaze.
* Configureable number of connector threads and buffer size in blaze-server.

# v0.9.3 (2015-08-27)
* Trampoline recursive calls in blaze ProcessWriter.
* Handle server hangup and body termination correctly in blaze client.

# v0.9.2 (2015-08-26)
* Bump http4s-websockets to 1.0.3 to properly decode continuation opcode.
* Fix metrics incompatibility when using Jetty 9.3 backend.
* Preserve original headers when appending as opposed to quoting.

# v0.8.5 (2015-08-26)
* Preserve original headers when appending as opposed to quoting.
* Upgrade to jawn-0.8.3 to avoid transitive dependency on GPL2 jmh

# v0.9.1 (2015-08-19)
* Fix bug in servlet nio handler.

# v0.9.0 (2015-08-15)
* Require Java8.
* `StaticFile` uses the filename extension exclusively to determine media-type.
* Add `/` method to `Uri`.
* Add `UrlFormLifter` middleware to aggregate url-form parameters with the query parameters.
* Add local address information to the `Request` type.
* Add a Http method 'or' (`|`) extractor.
* Add `VirtualHost` middleware for serving multiple sites from one server.
* Add websocket configuration to the blaze server builder.
* Redefine default timeout status code to 500.
* Redefine the `Service` arrow result from `Task[Option[_]]` to `Task[_]`.
* Don't extend `AllInstances` with `Http4s` omnibus import object.
* Use UTF-8 as the default encoding for text bodies.
* Numerous bug fixes by numerous contributors!

# v0.8.4 (2015-07-13)
* Honor the buffer size parameter in gzip middleware.
* Handle service exceptions in servlet backends.
* Respect asyncTimeout in servlet backends.
* Fix prefix mounting bug in blaze-server.
* Do not apply CORS headers to unsuccessful OPTIONS requests.

# v0.8.3 (2015-07-02)
* Fix bug parsing IPv4 addresses found in URI construction.

# v0.8.2 (2015-06-22)
* Patch instrumented handler for Jetty to time async contexts correctly.
* Fix race condition with timeout registration and route execution in blaze client
* Replace `ConcurrentHashMap` with synchronized `HashMap` in `staticcontent` package.
* Fix static content from jars by avoiding `"//"` in path uris when serving static content.
* Quote MediaRange extensions.
* Upgrade to jawn-streamz-0.5.0 and blaze-0.8.2.
* Improve error handling in blaze-client.
* Respect the explicit default encoding passed to `decodeString`.

# v0.8.1 (2015-06-16)
* Authentication middleware integrated into the server package.
* Static content tools integrated into the server package.
* Rename HttpParser to HttpHeaderParser and allow registration and removal of header parsers.
* Make UrlForm EntityDecoder implicitly resolvable.
* Relax UrlForm parser strictness.
* Add 'follow redirect' support as a client middleware.
* Add server middleware for auto retrying uris of form '/foo/' as '/foo'.
* Numerous bug fixes.
* Numerous version bumps.

# ~~v0.8.0 (2015-06-16)~~
* Mistake.  Go straight to v0.8.1.

# v0.7.0 (2015-05-05)
* Add QueryParamMatcher to the dsl which returns a ValidationNel.
* Dsl can differentiate between '/foo/' and '/foo'.
* Added http2 support for blaze backend.
* Added a metrics middleware usable on all server backends.
* Websockets are now modeled by an scalaz.stream.Exchange.
* Add `User-Agent` and `Allow` header types and parsers.
* Allow providing a Host header to the blaze client.
* Upgrade to scalaz-stream-7.0a.
* Added a CORS middleware.
* Numerous bug fixes.
* Numerous version bumps.

# v0.6.5 (2015-03-29)
* Fix bug in Request URI on servlet backend with non-empty context or servlet paths.
* Allow provided Host header for Blaze requests.

# v0.6.4 (2015-03-15)
* Avoid loading javax.servlet.WriteListener when deploying to a servlet 3.0 container.

# ~~v0.6.3 (2015-03-15)~~
* Forgot to pull origin before releasing.  Use v0.6.4 instead.

# v0.6.2 (2015-02-27)
* Use the thread pool provided to the Jetty servlet builder.
* Avoid throwing exceptions when parsing headers.
* Make trailing slash insignificant in service prefixes on servlet containers.
* Fix mapping of servlet query and mount prefix.

# v0.6.1 (2015-02-04)
* Update to blaze-0.5.1
* Remove unneeded error message (90b2f76097215)
* GZip middleware will not throw an exception if the AcceptEncoding header is not gzip (ed1b2a0d68a8)

# v0.6.0 (2015-01-27)

## http4s-core
* Remove ResponseBuilder in favor of Response companion.
* Allow '';'' separators for query pairs.
* Make charset on Message an Option.
* Add a `flatMapR` method to EntityDecoder.
* Various enhancements to QueryParamEncoder and QueryParamDecoder.
* Make Query an IndexedSeq.
* Add parsers for Location and Proxy-Authenticate headers.
* Move EntityDecoder.apply to `Request.decode` and `Request.decodeWith`
* Move headers into `org.http4s.headers` package.
* Make UriTranslation respect scriptName/pathInfo split.
* New method to resolve relative Uris.
* Encode query and fragment of Uri.
* Codec and wrapper type for URL-form-encoded bodies.

## http4s-server
* Add SSL support to all server builders.

## http4s-blaze-server
* Add Date header to blaze-server responses.
* Close connection when error happens during body write in blaze-server.

## http4s-servlet
* Use asynchronous servlet I/O on Servlet 3.1 containers.
* ServletContext syntax for easy mounting in a WAR deployment.
* Support Dropwizard Metrics collection for servlet containers.

## http4s-jawn
* Empty strings are a JSON decoding error.

## http4s-argonaut
* Add codec instances for Argonaut's CodecJson.

## http4s-json4s
* Add codec instances for Json4s' Reader/Writer.

## http4s-twirl
* New module to support Twirl templates

## http4s-scala-xml
* Split scala-xml support into http4s-scala-xml module.
* Change inferred type of `scala.xml.Elem` to `application/xml`.

## http4s-client
* Support for signing oauth-1 requests in client.

## http4s-blaze-client
* Fix blaze-client when receiving HTTP1 response without Content-Length header.
* Change default blaze-client executor to variable size.
* Fix problem with blaze-client timeouts.

# v0.5.4 (2015-01-08)
* Upgrade to blaze 0.4.1 to fix header parsing issue in blaze http/1.x client and server.

# v0.5.3 (2015-01-05)
* Upgrade to argonaut-6.1-M5 to match jawn. [#157](https://github.com/http4s/http4s/issues/157)

# v0.5.2 (2015-01-02)
* Upgrade to jawn-0.7.2.  Old version of jawn was incompatible with argonaut. [#157]](https://github.com/http4s/http4s/issues/157)

# v0.5.1 (2014-12-23)
* Include context path in calculation of scriptName/pathInfo. [#140](https://github.com/http4s/http4s/issues/140)
* Fix bug in UriTemplate for query params with multiple keys.
* Fix StackOverflowError in query parser. [#147](https://github.com/http4s/http4s/issues/147)
* Allow ';' separators for query pairs.

# v0.5.0 (2014-12-11)
* Client syntax has evloved and now will include Accept headers when used with EntityDecoder
* Parse JSON with jawn-streamz.
* EntityDecoder now returns an EitherT to make decoding failure explicit.
* Renamed Writable to EntityEncoder
* New query param typeclasses for encoding and decoding query strings.
* Status equality now discards the reason phrase.
* Match AttributeKeys as singletons.
* Added async timeout listener to servlet backends.
* Start blaze server asynchronously.
* Support specifying timeout and executor in blaze-client.
* Use NIO for encoding files.

# v0.4.2 (2014-12-01)
* Fix whitespace parsing in Authorization header [#87](https://github.com/http4s/http4s/issues/87)

# v0.4.1 (2014-11-20)
* `Uri.query` and `Uri.fragment` are no longer decoded. [#75](https://github.com/http4s/http4s/issues/75)

# v0.4.0 (2014-11-18)

* Change HttpService form a `PartialFunction[Request,Task[Response]]`
  to `Service[Request, Response]`, a type that encapsulates a `Request => Task[Option[Response]]`
* Upgrade to scalaz-stream-0.6a
* Upgrade to blaze-0.3.0
* Drop scala-logging for log4s
* Refactor ServerBuilders into an immutable builder pattern.
* Add a way to control the thread pool used for execution of a Service
* Modernize the Renderable/Renderer framework
* Change Renderable append operator from ~ to <<
* Split out the websocket codec and types into a seperate package
* Added ReplyException, an experimental way to allow an Exception to encode
  a default Response on for EntityDecoder etc.
* Many bug fixes and slight enhancements

# v0.3.0 (2014-08-29)

* New client API with Blaze implementation
* Upgrade to scalaz-7.1.0 and scalaz-stream-0.5a
* JSON Writable support through Argonaut and json4s.
* Add EntityDecoders for parsing bodies.
* Moved request and response generators to http4s-dsl to be more flexible to
  other frameworks'' syntax needs.
* Phased out exception-throwing methods for the construction of various
  model objects in favor of disjunctions and macro-enforced literals.
* Refactored imports to match the structure followed by [scalaz](https://github.com/scalaz/scalaz).

# v0.2.0 (2014-07-15)

* Scala 2.11 support
* Spun off http4s-server module. http4s-core is neutral between server and
the future client.
* New builder for running Blaze, Jetty, and Tomcat servers.
* Configurable timeouts in each server backend.
* Replace Chunk with scodec.bits.ByteVector.
* Many enhancements and bugfixes to URI type.
* Drop joda-time dependency for slimmer date-time class.
* Capitalized method names in http4s-dsl.

# v0.1.0 (2014-04-15)

* Initial public release.
