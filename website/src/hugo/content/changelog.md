---
menu: main
weight: 101
title: Changelog
---

Maintenance branches are merged before each new release. This change log is
ordered chronologically, so each release contains all changes described below
it.

# v0.19.0-SNAPSHOT

## Breaking changes
* [#2081](https://github.com/http4s/http4s/pull/2081): Remove `OkHttp` code redundant with `OkHttpBuilder`.
* [#2092](https://github.com/http4s/http4s/pull/2092): Remove `ExecutionContext` and `Timer` implicits from async-http-client. Threads are managed by the `ContextShift`.

## Enhancements
* [#2042](https://github.com/http4s/http4s/pull/2042): New `Throttle` server middleware
* [#2036](https://github.com/http4s/http4s/pull/2036): New `http4s-jetty-client` backend, with HTTP/2 support
* [#2080](https://github.com/http4s/http4s/pull/2080): Make `Http4sMatchers` polymorphic on their effect type
* [#2082](https://github.com/http4s/http4s/pull/2082): Structured parser for the `Origin` header
* [#2061](https://github.com/http4s/http4s/pull/2061): Send `Disconnect` event on EOF in blaze-server for faster cleanup of mid stages

## Bugfixes
* [#2069](https://github.com/http4s/http4s/pull/2069): Add proper `withMaxTotalConnections` method to `BlazeClientBuilder` in place of misnamed `withIdleTimeout` overload.

## Documentation updates
* [#2076](https://github.com/http4s/http4s/pull/2076): Align coloring of legend and table for milestone on versoins page
* [#2077](https://github.com/http4s/http4s/pull/2077): Replace Typelevel Code of Conduct with Scala Code of Conduct
* [#2083](https://github.com/http4s/http4s/pull/2083): Fix link to 0.19 on the website
* [#2100](https://github.com/http4s/http4s/pull/2100): Correct `re-start` to `reStart` in docs

## Dependency upgrades
* cats-1.4.0
* play-json-3.6.10 for Scala 2.11.x
* tomcat-9.0.12

# v0.18.18 (2018-09-18)

## Bug fixes
* [#2048](https://github.com/http4s/http4s/pull/2048): Correct misleading logging in `Retry` middleware
* [#2078](https://github.com/http4s/http4s/pull/2078): Replace generic exception on full wait queue with new `WaitQueueFullFailure`

## Enhancements
* [#2078](https://github.com/http4s/http4s/pull/2078): Replace generic exception on full wait queue with new `WaitQueueFullFailure`
* [#2095](https://github.com/http4s/http4s/pull/2095): Add `Monoid[UrlForm]` instance

## Dependency upgrades
* cats-1.4.0
* fs2-0.10.6
* jetty-9.4.12.v20180830
* tomcat-9.0.12

# v0.19.0-M2 (2018-09-07)

## Breaking changes
* [#1802](https://github.com/http4s/http4s/pull/1802): Race servlet requests against the `AsyncContext.timeout`. `JettyBuilder` and `TomcatBuilder` now require a `ConcurrentEffect` instance.
* [#1934](https://github.com/http4s/http4s/pull/1934): Refactoring of `ConnectionManager`.  Now requires a `Concurrent` instance, which ripples to a `ConcurrentEffect` in blaze-client builders
* [#2023](https://github.com/http4s/http4s/pull/2023): Don't overwrite existing `Vary` headers from `CORS`
* [#2030](https://github.com/http4s/http4s/pull/2023): Restrict `MethodNotAllowed` response generator in DSL
* [#2032](https://github.com/http4s/http4s/pull/2032): Eliminate mutable `Status` registry. IANA-registered `Status`es are still cached, but `register` is no longer public.
* [#2026](https://github.com/http4s/http4s/pull/2026): `CSRF` enhancements
  * CSRF tokens represented with a newtype
  * CSRF token signatures are encoded hexadecimal strings, making them URI-safe.
  * Added a `headerCheck: Request[F] => Boolean` parameter
  * Added an `onFailure: Response[F]` parameter, which defaults to a `403`. This was formerly a hardcoded `401`.
* [#1993](https://github.com/http4s/http4s/pull/2026): Massive changes from cats-effect and fs2 upgrades
  * `Timer` added to `AsyncHttpClient`
  * Dropwizard `Metrics` middleware now takes a `Clock` rather than a `Timer`
  * Client builders renamed and refactored for consistency and to support binary compatible evolution after 1.0:
    * `BlazeClientBuilder` replaces `Http1Client`, `BlazeClient`, and `BlazeClientConfig`
    * Removed deprecated `SimpleHttp1Client`
    * `JavaNetClient` renamed to `JavaNetClientBuilder`, which now has a `resource` and `stream`
    * `OkHttp` renamed to `OkHttpBuilder`.  The client now created from an `OkHttpClient` instance instead of an `F[OkHttpClient.Builder]`. A default client can be created as a `Resource` through `OkHttp.default`.
  * Fallout from removal of `fs2.Segment`
    * `EntityDecoder.collectBinary` now decodes a `Chunk`
    * `EntityDecoder.binaryChunk` deprecated
    * `SegmentWriter` is removed
    * Changes to:
      * `ChunkWriter`s in blaze rewritten
      * `Logger` middlewares
      * `MemoryCache`
  * Blocking I/O now requires a blocking `ExecutionContext` and a `ContextShift`:
    * `EntityDecoder`s:
      * `EntityDecoder.binFile`
      * `EntityDecoder.textFile`
      * `MultipartDecoder.mixedMultipart`
    * `EntityEncoder`s (no longer implicit):
      * `File`
      * `Path`
      * `InputStream`
      * `Reader`
    * Multipart:
      * `MultipartParser.parseStreamedFile`
      * `MultipartParser.parseToPartsStreamedFile`
      * `Part.fileData`
    * Static resources:
      * `StaticFile.fromString`
      * `StaticFile.fromResource`
      * `StaticFile.fromURL`
      * `StaticFile.fromFile`
      * `FileService.Config`
      * `ResourceService.Config`
      * `WebjarService.Config`
    * `OkHttpBuilder`
    * Servlets:
      * `BlockingHttp4sServlet`
      * `BlockingServletIo`
  * Servlet backend changes:
    * `Http4sServlet` no longer shift onto an `ExecutionContext` by default.  Accordingly, `ServerBuilder` no longer has a `withExecutionContext`.
    * Jetty and Tomcat builders use their native executor types instead of shifting onto an `ExecutionContext`.  Accordingly, `ServletBuilder#withExecutionContext` is removed.
    * `AsyncHttp4sServlet` and `ServletContextSyntax` now default to non-blocking I/O.  No startup check is made against the servlet version, which failed classloading on an older servlet container.  Neither takes an `ExeuctionContext` parameter anymore.
  * Removed deprecated `StreamApp` aliases. `fs2.StreamApp` is removed and replaced by `cats.effect.IOApp`, `monix.eval.TaskApp`, or similar.
  * Removed deprecated `ServerApp`.
  * `EntityLimiter` middleware now requires an `ApplicativeError`
* [#2054](https://github.com/http4s/http4s/pull/2054): blaze-server builder changes
  * `BlazeBuilder` deprecated for `BlazeServerBuilder`
  * `BlazeServerBuidler` has a single `withHttpApp(HttpApp)` in place of zero-to-many calls `mountService(HttpRoutes)`.
    * This change makes it possible to mount an `HttpApp` wrapped in a `Logger` middleware, which only supports `HttpApp`
    * Call `.orNotFound`, from `org.http4s.implicits._`, to cap an `HttpRoutes` as `HttpApp`
    * Use `Router` to combine multiple `HttpRoutes` into a single `HttpRoutes` by prefix
    * This interface will see more changes before 0.19.0 to promote long-term binary compatibility

## Enhancements
* [#1953](https://github.com/http4s/http4s/pull/1953): Add `UUIDVar` path extractor
* [#1963](https://github.com/http4s/http4s/pull/1963): Throw `ConnectException` rather than `IOException` on blaze-client connection failures
* [#1961](https://github.com/http4s/http4s/pull/1961): New `http4s-prometheus-client-metrics` module
* [#1974](https://github.com/http4s/http4s/pull/1974): New `http4s-client-metrics` module for Dropwizard Metrics
* [#1973](https://github.com/http4s/http4s/pull/1973): Add `onClose` handler to `WebSocketBuilder`
* [#2024](https://github.com/http4s/http4s/pull/2024): Add `HeaderEcho` server middleware
* [#2062](https://github.com/http4s/http4s/pull/2062): Eliminate "unhandled inbund command: Disconnected"` warnings in blaze-server

## Bugfixes
* [#2027](https://github.com/http4s/http4s/pull/2024): Miscellaneous websocket fixes
  * Stop sending frames even after closed
  * Avoid deadlock on small threadpools
  * Send `Close` frame in response to `Close` frame

## Documentation updates
* [#1935](https://github.com/http4s/http4s/pull/1953): Make `http4sVersion` lowercase
* [#1943](https://github.com/http4s/http4s/pull/1943): Make the imports in the Client documentation silent
* [#1944](https://github.com/http4s/http4s/pull/1944): Upgrade to cryptobits-1.2
* [#1971](https://github.com/http4s/http4s/pull/1971): Minor corrections to DSL tut
* [#1972](https://github.com/http4s/http4s/pull/1972): Add `UUIDVar` to DSL tut
* [#2034](https://github.com/http4s/http4s/pull/1958): Add branch to quickstart instructions
* [#2035](https://github.com/http4s/http4s/pull/2035): Add Christopher Davenport to community staff
* [#2060](https://github.com/http4s/http4s/pull/2060): Guide to setting up IntelliJ for contributors

## Internal
* [#1966](https://github.com/http4s/http4s/pull/1966): Use scalafmt directly from IntelliJ
* [#1968](https://github.com/http4s/http4s/pull/1968): Build with sbt-1.2.1
* [#1996](https://github.com/http4s/http4s/pull/1996): Internal refactoring of `JettyBuilder`
* [#2041](https://github.com/http4s/http4s/pull/2041): Simplify implementations of `RetryPolicy`
* [#2050](https://github.com/http4s/http4s/pull/2050): Replace test `ExecutionContext` in `Http4sWSStageSpec`
* [#2052](https://github.com/http4s/http4s/pull/2050): Introduce expiring `TestScheduler` to avoid leaking threads on tests

## Dependency upgrades
* async-http-client-2.5.2
* blaze-0.14.0-M4
* cats-1.3.1
* cats-effect-1.0.0
* circe-0.10.0-M2
* fs2-1.0.0-M5
* jawn-0.13.0
* jawn-fs2-0.13.0-M4
* json4s-3.6.0

# v0.18.17 (2018-09-04)
* Accumulate errors in `OptionalMultiQueryParamDecoderMatcher` [#2000](https://github.com/http4s/pull/2000)
* New http4s-scalatags module [#2002](https://github.com/http4s/pull/2002)
* Resubmit bodies in `Retry` middleware where allowed by policy [#2001](https://github.com/http4s/pull/2001)
* Dependency upgrades:
  * play-json-3.6.10 (for Scala 2.12)
  * tomcat-9.0.11

# v0.18.16 (2018-08-14)
* Fix regression for `AutoSlash` when nested in a `Router` [#1948](https://github.com/http4s/http4s/pull/1948)
* Respect `redactHeadersWhen` in `Logger` middleware [#1952](https://github.com/http4s/http4s/pull/1952)
* Capture `BufferPoolsExports` in prometheus server middleware [#1977](https://github.com/http4s/http4s/pull/1977)
* Make `Referer` header extractable [#1984](https://github.com/http4s/http4s/pull/1984)
* Log server startup banner in a single call to prevent interspersion [#1985](https://github.com/http4s/http4s/pull/1985)
* Add support module for play-json [#1946](https://github.com/http4s/http4s/pull/1946)
* Introduce `TranslateUri` middleware, which checks the prefix of the service it's translating against the request. Deprecated `URITranslation`, which chopped the prefix length without checking for a match. [#1964](https://github.com/http4s/http4s/pull/1964)
* Dependency upgrades:
  * cats-1.2.0
  * metrics-4.0.3
  * okhttp-3.11.0
  * prometheus-client-0.5.0
  * scodec-bits-1.1.6

# v0.18.15 (2018-07-05)
* Bugfix for `AutoSlash` Middleware in Router [#1937](https://github.com/http4s/http4s/pull/1937)
* Add `StaticHeaders` middleware that appends static headers to a service [#1939](https://github.com/http4s/http4s/pull/1939)

# v0.19.0-M1 (2018-07-04)
* Add accumulating version of circe `EntityDecoder` [#1647](https://github.com/http4/http4s/1647)
* Add ETag support to `StaticFile` [#1652](https://github.com/http4s/http4s/pull/1652)
* Reintroduce the option for fallthrough for authenticated services [#1670]((https://github.com/http4s/http4s/pull/1670)
* Separate `Cookie` into `RequestCookie` and `ResponseCookie` [#1676](https://github.com/http4s/http4s/pull/1676)
* Add `Eq[Uri]` instance [#1688](https://github.com/http4s/http4s/pull/1688)
* Deprecate `Message#withBody` in favor of `Message#withEntity`.  The latter returns a `Message[F]` rather than an `F[Message[F]]`. [#1694](https://github.com/http4s/http4s/pull/1694)
* Myriad new `Arbitrary` and `Cogen` instances [#1677](https://github.com/http4s/http4s/pull/1677)
* Add non-deprecated `LocationResponseGenerator` functions [#1715](https://github.com/http4s/http4s/pull/1715)
* Relax constraint on `Router` from `Sync` to `Monad` [#1723](https://github.com/http4s/http4s/pull/1723)
* Drop scodec-bits dependency [#1732](https://github.com/http4s/http4s/pull/1732)
* Add `Show[ETag]` instance [#1749](https://github.com/http4s/http4s/pull/1749)
* Replace `fs2.Scheduler` with `cats.effect.Timer` in `Retry` [#1754](https://github.com/http4s/http4s/pull/1754)
* Remove `Sync` constraint from `EntityEncoder[Multipart]` [#1762](https://github.com/http4s/http4s/pull/1762)
* Generate `MediaType`s from [MimeDB](https://github.com/jshttp/mime-db) [#1770](https://github.com/http4s/http4s/pull/1770)
  * Continue phasing out `Renderable` with `MediaRange` and `MediaType`.
  * Media types are now namespaced by main type.  This reduces backticks.  For example, `` MediaType.`text/plain` `` is replaced by `MediaType.text.plain`.
* Remove `Registry`. [#1770](https://github.com/http4s/http4s/pull/1770)
* Deprecate `HttpService`: [#1693](https://github.com/http4s/http4s/pull/1693)
  * Introduces an `Http[F[_], G[_]]` type alias
  * `HttpService` is replaced by `HttpRoutes`, which is an `Http[OptionT[F, ?], ?]`.  `HttpRoutes.of` replaces `HttpService` constructor from `PartialFunction`s.
  * `HttpApp` is an `Http[F, F]`, representing a total HTTP function.
* Add `BlockingHttp4sServlet` for use in Google App Engine and Servlet 2.5 containers.  Rename `Http4sServlet` to `AsyncHttp4sServlet`. [#1830](https://github.com/http4s/http4s/pull/1830)
* Generalize `Logger` middleware to log with `String => Unit` instead of `logger.info(_)` [#1839](https://github.com/http4s/http4s/pull/1839)
* Generalize `AutoSlash` middleware to work on `Kleisli[F, Request[G], B]` given `MonoidK[F]` and `Functor[G]`. [#1885](https://github.com/http4s/http4s/pull/1885)
* Generalize `CORS` middleware to work on `Http[F, G]` given `Applicative[F]` and `Functor[G]`. [#1889](https://github.com/http4s/http4s/pull/1889)
* Generalize `ChunkAggegator` middleware to work on `Kleisli[F, A, Response[G]]` given `G ~> F`, `FlatMap[F]`, and `Sync[G]`. [#1886](https://github.com/http4s/http4s/pull/1886)
* Generalize `EntityLimiter` middleware to work on `Kleisli[F, Request[G], B]`. [#1892](https://github.com/http4s/http4s/pull/1892)
* Generalize `HSTS` middleware to work on `Kleisli[F, A, Response[G]]` given `Functor[F]` and `Functor[G]`. [#1893](https://github.com/http4s/http4s/pull/1893)
* Generalize `UrlFormLifter` middleware to work on `Kleisli[F, Request[G], Response[G]]` given `G ~> F`, `Sync[F]` and `Sync[G]`.  [#1894](https://github.com/http4s/http4s/pull/1894)
* Generalize `Timeout` middleware to work on `Kleisli[F, A, Response[G]]` given `Concurrent[F]` and `Timer[F]`. [#1899](https://github.com/http4s/http4s/pull/1899)
* Generalize `VirtualHost` middleware to work on `Kleisli[F, Request[G], Response[G]]` given `Applicative[F]`.  [#1902](https://github.com/http4s/http4s/pull/1902)
* Generalize `URITranslate` middleware to work on `Kleisli[F, Request[G], B]` given `Functor[G]`.  [#1895](https://github.com/http4s/http4s/pull/1895)
* Generalize `CSRF` middleware to work on `Kleisli[F, Request[G], Response[G]]` given `Sync[F]` and `Applicative[G]`.  [#1909](https://github.com/http4s/http4s/pull/1909)
* Generalize `ResponseLogger` middleware to work on `Kleisli[F, A, Response[F]]` given `Effect[F]`.  [#1916](https://github.com/http4s/http4s/pull/1916)
* Make `Logger`, `RequestLogger`, and `ResponseLogger` work on `HttpApp[F]` so a `Response` is guaranteed unless the service raises an error [#1916](https://github.com/http4s/http4s/pull/1916)
* Rename `RequestLogger.apply0` and `ResponseLogger.apply0` to `RequestLogger.apply` and `ResponseLogger.apply`.  [#1837](https://github.com/http4s/http4s/pull/1837)
* Move `org.http4s.server.ServerSoftware` to `org.http4s.ServerSoftware` [#1884](https://github.com/http4s/http4s/pull/1884)
* Fix `Uncompressible` and `NotBinary` flags in `MimeDB` generator. [#1900](https://github.com/http4s/http4s/pull/1884)
* Generalize `DefaultHead` middleware to work on `Http[F, G]` given `Functor[F]` and `MonoidK[F]` [#1903](https://github.com/http4s/http4s/pull/1903)
* Generalize `GZip` middleware to work on `Http[F, G]` given `Functor[F]` and `Functor[G]` [#1903](https://github.com/http4s/http4s/pull/1903)
* `jawnDecoder` takes a `RawFacade` instead of a `Facade`
* Change `BasicCredentials` extractor to return `(String, String)` [#1924](https://github.com/http4s/http4s/1925)
* `Effect` constraint relaxed to `Sync`:
  * `Logger.logMessage`
* `Effect` constraint relaxed to `Async`:
  * `JavaNetClient`
* `Effect` constraint changed to `Concurrent`:
  * `Logger` (client and server)
  * `RequestLogger` (client and server)
  * `ResponseLogger` (client and server)
  * `ServerBuilder#serve` (moved to abstract member of `ServerBuilder`)
* `Effect` constraint strengthened to `ConcurrentEffect`:
  * `AsyncHttpClient`
  * `BlazeBuilder`
  * `JettyBuilder`
  * `TomcatBuilder`
* Implicit `ExecutionContext` removed from:
  * `RequestLogger` (client and server)
  * `ResponseLogger` (client and server)
  * `ServerBuilder#serve`
  * `ArbitraryInstances.arbitraryEntityDecoder`
  * `ArbitraryInstances.cogenEntity`
  * `ArbitraryInstances.cogenEntityBody`
  * `ArbitraryInstances.cogenMessage`
  * `JavaNetClient`
* Implicit `Timer` added to:
  * `AsyncHttpClient`
  * `JavaNetClient.create`
* `Http4sWsStage` removed from public API
* Removed charset for argonaut instances [#1914](https://github.com/http4s/http4s/pull/1914)
* Dependency upgrades:
  * async-http-client-2.4.9
  * blaze-0.14.0-M3
  * cats-effect-1.0.0-RC2
  * circe-0.10.0-M1
  * fs2-1.0.0-M1
  * fs2-reactive-streams-0.6.0
  * jawn-0.12.1
  * jawn-fs2-0.13.0-M1
  * prometheus-0.4.0
  * scala-xml-1.1.0

# v0.18.14 (2018-07-03)
* Add `CirceEntityCodec` to provide an implicit `EntityEncoder` or `EntityDecoder` from an `Encoder` or `Decoder`, respectively. [#1917](https://github.com/http4s/http4s/pull/1917)
* Add a client backend based on `java.net.HttpURLConnection`.  Note that this client blocks and is primarily intended for use in a REPL. [#1882](https://github.com/http4s/http4s/pull/1882)
* Dependency upgrades:
  * jetty-9.4.11
  * tomcat-9.0.10
	
# v0.18.13 (2018-06-22)
* Downcase type in `MediaRange` generator [#1907](https://github.com/http4s/http4s/pull/1907)
* Fixed bug where `PoolManager` would try to dequeue from an empty queue [#1922](https://github.com/http4s/http4s/pull/1922)
* Dependency upgrades:
  * argonaut-6.2.2
  * fs2-0.10.5

# v0.18.12 (2018-05-28)
* Deprecated `Part.empty` [#1858](https://github.com/http4s/http4s/pull/1858)
* Log requests with an unconsumed body [#1861](https://github.com/http4s/http4s/pull/1861)
* Log requests when the service returns `None` or raises an error [#1875](https://github.com/http4s/http4s/pull/1875)
* Support streaming parsing of multipart and storing large parts as temp files [#1865](https://github.com/http4s/http4s/pull/1865)
* Add an OkHttp client, with HTTP/2 support [#1864](https://github.com/http4s/http4s/pull/1864)
* Add `Host` header to requests to `Client.fromHttpService` if the request URI is absolute [#1874](https://github.com/http4s/http4s/pull/1874)
* Log `"service returned None"` or `"service raised error"` in service `ResponseLogger` when the service does not produce a successful response [#1879](https://github.com/http4s/http4s/pull/1879)
* Dependency upgrades:
  * jetty-9.4.10.v20180503
  * json4s-3.5.4
  * tomcat-9.0.8

# v0.18.11 (2018-05-10)
* Prevent zero-padding of servlet input chunks [#1835](https://github.com/http4s/http4s/pull/1835)
* Fix deadlock in client loggers.  `RequestLogger.apply` and `ResponseLogger.apply` are each replaced by `apply0` to maintain binary compatibility. [#1837](https://github.com/http4s/http4s/pull/1837)
* New `http4s-boopickle` module supports entity codecs through `boopickle.Pickler` [#1826](https://github.com/http4s/http4s/pull/1826)
* Log as much of the response as is consumed in the client. Previously, failure to consume the entire body prevented any part of the body from being logged. [#1846](https://github.com/http4s/http4s/pull/1846)
* Dependency upgrades:
  * prometheus-client-java-0.4.0

# v0.18.10 (2018-05-03)
* Eliminate dependency on Macro Paradise and macro-compat [#1816](https://github.com/http4s/http4s/pull/1816)
* Add `Logging` middleware for client [#1820](https://github.com/http4s/http4s/pull/1820)
* Make blaze-client tick wheel executor lazy [#1822](https://github.com/http4s/http4s/pull/1822)
* Dependency upgrades:
  * cats-effect-0.10.1
  * fs2-0.10.4
  * specs2-4.1.0

# v0.18.9 (2018-04-17)
* Log any exceptions when writing the header in blaze-server for HTTP/1 [#1781](https://github.com/http4s/http4s/pull/1781)
* Drain the response body (thus running its finalizer) when there is an error writing a servlet header or body [#1782](https://github.com/http4s/http4s/pull/1782)
* Clean up logging of errors thrown by services. Prevents the possible swallowing of errors thrown during `renderResponse` in blaze-server and `Http4sServlet` [#1783](https://github.com/http4s/http4s/pull/1783)
* Fix `Uri.Scheme` parser for schemes beginning with `http` other than `https` [#1790](https://github.com/http4s/http4s/pull/1790)
* Fix blaze-client to reset the connection start time on each invocation of the `F[DisposableResponse]`. This fixes the "timeout after 0 milliseconds" error. [#1792](https://github.com/http4s/http4s/pull/1792)
* Depdency upgrades:
  * blaze-0.12.13
  * http4s-websocket-0.2.1
  * specs2-4.0.4
  * tomcat-9.0.7

# v0.18.8 (2018-04-11)
* Improved ScalaDoc for BlazeBuilder [#1775](https://github.com/http4s/http4s/pull/1775)
* Added a stream constructor for async-http-client [#1776](https://github.com/http4s/http4s/pull/1776)
* http4s-prometheus-server-metrics project created. Prometheus Metrics middleware implemented for metrics on http4s server. Exposes an HttpService ready to be scraped by Prometheus, as well pairing to a CollectorRegistry for custom metric registration. [#1778](https://github.com/http4s/http4s/pull/1778)

# v0.18.7 (2018-04-04)
* Multipart parser defaults to fields interpreted as utf-8. [#1767](https://github.com/http4s/http4s/pull/1767)

# v0.18.6 (2018-04-03)
* Fix parsing of multipart bodies across chunk boundaries. [#1764](https://github.com/http4s/http4s/pull/1764)

# v0.18.5 (2018-03-28)
* Add `&` extractor to http4s-dsl. [#1758](https://github.com/http4s/http4s/pull/1758)
* Deprecate `EntityEncoder[F, Future[A]]`.  The `EntityEncoder` is strict in its argument, which causes any side effect of the `Future` to execute immediately.  Wrap your `future` in `IO.fromFuture(IO(future))` instead. [#1759](https://github.com/http4s/http4s/pull/1759)
* Dependency upgrades:
  * circe-0.9.3

# v0.18.4 (2018-03-23)
* Deprecate old `Timeout` middleware methods in favor of new ones that use `FiniteDuration` and cancel timed out effects [#1725](https://github.com/http4s/http4s/pull/1725)
* Add `expectOr` methods to client for custom error handling on failed expects [#1726](https://github.com/http4s/http4s/pull/1726)
* Replace buffered multipart parser with a streaming version. Deprecate all uses of fs2-scodec. [#1727](https://github.com/http4s/http4s/pull/1727)
* Dependency upgrades:
  * blaze-0.12.2
  * fs2-0.10.3
  * log4s-1.6.1
  * jetty-9.4.9.v20180320

# v0.18.3 (2018-03-17)
* Remove duplicate logging in pool manager [#1683]((https://github.com/http4s/http4s/pull/1683)
* Add request/response specific properties to logging [#1709](https://github.com/http4s/http4s/pull/1709)
* Dependency upgrades:
  * async-http-client-2.0.39
  * cats-1.1.0
  * cats-effect-0.10
  * circe-0.9.2
  * discipline-0.9.0
  * jawn-fs2-0.12.2
  * log4s-1.5.0
  * twirl-1.3.15

# v0.18.2 (2018-03-09)
* Qualify reference to `identity` in `uriLiteral` macro [#1697](https://github.com/http4s/http4s/pull/1697)
* Make `Retry` use the correct duration units [#1698](https://github.com/http4s/http4s/pull/1698)
* Dependency upgrades:
  * tomcat-9.0.6

# v0.18.1 (2018-02-27)
* Fix the rendering of trailer headers in blaze [#1629](https://github.com/http4s/http4s/pull/1629)
* Fix race condition between shutdown and parsing in Http1SeverStage [#1675](https://github.com/http4s/http4s/pull/1675)
* Don't use filter in `Arbitrary[``Content-Length``]` [#1678](https://github.com/http4s/http4s/pull/1678)
* Opt-in fallthrough for authenticated services [#1681](https://github.com/http4s/http4s/pull/1681)
* Dependency upgrades:
  * cats-effect-0.9
  * fs2-0.10.2
  * fs2-reactive-streams-0.5.1
  * jawn-fs2-0.12.1
  * specs2-4.0.3
  * tomcat-9.0.5
  * twirl-1.3.4

# v0.18.0 (2018-02-01)
* Add `filename` method to `Part`
* Dependency upgrades:
  * fs2-0.10.0
  * fs2-reactive-streams-0.5.0
  * jawn-fs2-0.12.0

# v0.18.0-M9 (2018-01-26)
* Emit Exit Codes On Server Shutdown [#1638](https://github.com/http4s/http4s/pull/1638) [#1637](https://github.com/http4s/http4s/pull/1637)
* Register Termination Signal and Frame in Http4sWSStage [#1631](https://github.com/http4s/http4s/pull/1631)
* Trailer Headers Are Now Being Emitted Properly [#1629](https://github.com/http4s/http4s/pull/1629)
* Dependency Upgrades:
   * alpn-boot-8.1.12.v20180117
   * circe-0.9.1
   * fs2-0.10.0-RC2
   * fs2-reactive-streams-0.3.0
   * jawn-fs2-0.12.0-M7
   * metrics-4.0.2
   * tomcat-9.0.4

# v0.18.0-M8 (2018-01-05)
* Dependency Upgrades:
   * argonaut-6.2.1
   * circe-0.9.0
   * fs2-0.10.0-M11
   * fs2-reactive-streams-0.2.8
   * jawn-fs2-0.12.0-M6
   * cats-1.0.1
   * cats-effect-0.8

# v0.18.0-M7 (2017-12-23)
* Relax various typeclass constraints from `Effect` to `Sync` or `Async`. [#1587](https://github.com/http4s/http4s/pull/1587)
* Operate on `Segment` instead of `Chunk` [#1588](https://github.com/http4s/http4s/pull/1588)
   * `EntityDecoder.collectBinary` and `EntityDecoder.binary` now
     return `Segment[Byte, Unit]` instead of `Chunk[Byte]`.
   * Add `EntityDecoder.binaryChunk`.
   * Add `EntityEncoder.segmentEncoder`.
   * `http4sMonoidForChunk` replaced by `http4sMonoidForSegment`.
* Add new generators for core RFC 2616 types. [#1593](https://github.com/http4s/http4s/pull/1593)
* Undo obsolete copying of bytes in `StaticFile.fromURL`. [#1202](https://github.com/http4s/http4s/pull/1202)
* Optimize conversion of `Chunk.Bytes` and `ByteVectorChunk` to `ByteBuffer. [#1602](https://github.com/http4s/http4s/pull/1602)
* Rename `read` to `send` and `write` to `receive` in websocket model. [#1603](https://github.com/http4s/http4s/pull/1603)
* Remove `MediaRange` mutable `Registry` and add `HttpCodec[MediaRange]` instance [#1597](https://github.com/http4s/http4s/pull/1597)
* Remove `Monoid[Segment[A, Unit]]` instance, which is now provided by fs2. [#1609](https://github.com/http4s/http4s/pull/1609)
* Introduce `WebSocketBuilder` to build `WebSocket` responses.  Allows headers (e.g., `Sec-WebSocket-Protocol`) on a successful handshake, as well as customization of the response to failed handshakes. [#1607](https://github.com/http4s/http4s/pull/1607)
* Don't catch exceptions thrown by `EntityDecoder.decodeBy`. Complain loudly in logs about exceptions thrown by `HttpService` rather than raised in `F`. [#1592](https://github.com/http4s/http4s/pull/1592)
* Make `abnormal-terminations` and `service-errors` Metrics names plural. [#1611](https://github.com/http4s/http4s/pull/1611)
* Refactor blaze client creation. [#1523](https://github.com/http4s/http4s/pull/1523)
   * `Http1Client.apply` returns `F[Client[F]]`
   * `Http1Client.stream` returns `Stream[F, Client[F]]`, bracketed to shut down the client.
   * `PooledHttp1Client` constructor is deprecated, replaced by the above.
   * `SimpleHttp1Client` is deprecated with no direct equivalent.  Use `Http1Client`.
* Improve client timeout and wait queue handling
   * `requestTimeout` and `responseHeadersTimeout` begin from the submission of the request.  This includes time spent in the wait queue of the pool. [#1570](https://github.com/http4s/http4s/pull/1570)
   * When a connection is `invalidate`d, try to unblock a waiting request under the same key.  Previously, the wait queue would only be checked on recycled connections.
   * When the connection pool is closed, allow connections in the wait queue to complete.
* Changes to Metrics middleware. [#1612](https://github.com/http4s/http4s/pull/1612)
   * Decrement the active requests gauge when no request matches
   * Don't count non-matching requests as 4xx in case they're composed with other services.
   * Don't count failed requests as 5xx in case they're recovered elsewhere.  They still get recorded as `service-error`s.
* Dependency upgrades:
   * async-http-client-2.0.38
   * cats-1.0.0.RC2
   * circe-0.9.0-M3
   * fs2-0.10.0-M10
   * fs2-jawn-0.12.0-M5
   * fs2-reactive-streams-0.2.7
   * scala-2.10.7 and scala-2.11.12

# v0.18.0-M6 (2017-12-08)
* Tested on Java 9.
* `Message.withContentType` now takes a `Content-Type` instead of an
  ``Option[`Content-Type`]``.  `withContentTypeOption` takes an `Option`,
  and `withoutContentType` clears it.
* `QValue` has an `HttpCodec` instance
* `AuthMiddleware` never falls through.  See
  [#1530](https://github.com/http4s/http4s/pull/1530) for more.
* `ContentCoding` is no longer a `Registry`, but has an `HttpCodec`
  instance.
* Render a banner on server startup.  Customize by calling
  `withBanner(List[String])` or `withoutBanner` on the
  `ServerBuilder`.
* Parameterize `isZippable` as a predicate of the `Response` in `GZip`
  middleware.
* Add constant for `application/vnd.api+json` MediaType.
* Limit memory consumption in `GZip` middleware
* Add `handleError`, `handleErrorWith`, `bimap`, `biflatMap`,
  `transform`, and `transformWith` to `EntityDecoder`.
* `org.http4s.util.StreamApp` and `org.http4s.util.ExitCode` are
  deprecated in favor of `fs2.StreamApp` and `fs2.StreamApp.ExitCode`,
  based on what was in http4s.
* Dependency upgrades:
  * fs2-0.10.0-M9
  * fs2-reactive-streams-0.2.6
  * jawn-fs2-0.12.0-M4
  * specs2-4.0.2

# v0.17.6 (2017-12-05)
* Fix `StaticFile` to serve files larger than `Int.MaxValue` bytes
* Dependency upgrades:
  * tomcat-8.5.24

# v0.16.6 (2017-12-04)
* Add a CSRF server middleware
* Fix `NullPointerException` when starting a Tomcat server related to `docBase`
* Log version info and server address on server startup
* Dependency upgrades:
  * jetty-9.4.8.v20171121
  * log4s-1.4.0
  * scalaz-7.2.17
  * twirl-1.3.13

# v0.18.0-M5 (2017-11-02)
* Introduced an `HttpCodec` type class that represents a type that can round
  trip to and from a `String`.  `Uri.Scheme` and `TransferCoding` are the first
  implementors, with more to follow.  Added an `HttpCodecLaws` to http4s-testing.
* `Uri.Scheme` is now its own type instead of a type alias.
* `TransferCoding` is no longer a case class. Its `coding` member is now a
  `String`, not a `CaseInsensitiveString`. Its companion is no longer a
  `Registry`.
* Introduced `org.http4s.syntax.literals`, which contains a `StringContext` forAll
  safely constructing a `Uri.Scheme`.  More will follow.
* `org.http4s.util.StreamApp.ExitCode` moved to `org.http4s.util.ExitCode`
* Changed `AuthService[F[_], T]` to `AuthService[T, F[_]]` to support
  partial unification when combining services as a `SemigroupK`.
* Unseal the `MessageFailure` hierarchy. Previous versions of http4s had a
  `GenericParsingFailure`, `GenericDecodeFailure`, and
  `GenericMessageBodyFailure`. This was not compatible with the parameterized
  effect introduced in v0.18. Now, `MessageFailure` is unsealed, so users
  wanting precise control over the default `toHttpResponse` can implement their
  own failure conditions.
* `MessageFailure` now has an `Option[Throwable]` cause.
* Removed `KleisliInstances`. The `SemigroupK[Kleisli[F, A, ?]]` is now provided
  by cats.  Users should no longer need to import `org.http4s.implicits._` to
  get `<+>` composition of `HttpService`s
* `NonEmptyList` extensions moved from `org.http4s.util.nonEmptyList` to
  `org.http4s.syntax.nonEmptyList`.
* There is a classpath difference in log4s version between blaze and http4s in this
  milestone that will be remedied in M6. We believe these warnings are safe.
* Dependency upgrades:
  * cats-1.0.0-RC1
  * fs2-0.10.0-M8
  * fs2-reactive-streams-0.2.5

# v0.18.0-M4 (2017-10-12)
* Syntax for building requests moved from `org.http4s.client._` to
  `org.http4s.client.dsl.Http4sClientDsl[F]`, with concrete type `IO`
  available as `org.http4s.client.dsl.io._`.  This is consistent with
  http4s-dsl for servers.
* Change `StreamApp` to return a `Stream[F, ExitCode]`. The first exit code
  returned by the stream is the exit code of the JVM. This allows custom exit
  codes, and eases dead code warnings in certain constructions that involved
  mapping over `Nothing`.
* `AuthMiddleware.apply` now takes an `Kleisli[OptionT[F, ?], Request[F], T]`
  instead of a `Kleisli[F, Request[F], T]`.
* Set `Content-Type` header on default `NotFound` response.
* Merges from v0.16.5 and v0.17.5.
* Remove mutable map that backs `Method` registry. All methods in the IANA
  registry are available through `Method.all`. Custom methods should be memoized
  by other means.
* Adds an `EntityDecoder[F, Array[Byte]]` and `EntityDecoder[F, Array[Char]]`
  for symmetry with provided `EntityEncoder` instances.
* Adds `Arbitrary` instances for `Headers`, `EntityBody[F]` (currently just
  single chunk), `Entity[F]`, and `EntityEncoder[F, A]`.
* Adds `EntityEncoderLaws` for `EntityEncoder`.
* Adds `EntityCodecLaws`.  "EntityCodec" is not a type in http4s, but these
  laws relate an `EntityEncoder[F, A]` to an `EntityDecoder[F, A]`.
* There is a classpath difference in log4s version between blaze and http4s in this
  milestone that will be remedied in M6. We believe these warnings are safe.

# v0.17.5 (2017-10-12)
* Merges only.

# v0.16.5 (2017-10-11)
* Correctly implement sanitization of dot segments in static file paths
  according to RFC 3986 5.2.4. Most importantly, this fixes an issue where `...`
  is reinterpreted as `..` and can escape the root of the static file service.

# v0.18.0-M3 (2017-10-04)
* Merges only.
* There is a classpath difference in log4s version between blaze and http4s in this
  milestone that will be remedied in M6. We believe these warnings are safe.

# v0.17.4 (2017-10-04)
* Fix reading of request body in non-blocking servlet backend. It was previously
  only reading the first byte of each chunk.
* Dependency upgrades:
  * fs2-reactive-streams-0.1.1

# v0.16.4 (2017-10-04)
* Backport removal `java.xml.bind` dependency from `GZip` middleware,
  to play more nicely with Java 9.
* Dependency upgrades:
  * metrics-core-3.2.5
  * tomcat-8.0.23
  * twirl-1.3.12

# v0.18.0-M2 (2017-10-03)
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
* Add `message.decodeJson[A]` syntax to replace awkward `message.as(implicitly,
  jsonOf[A])`. Brought into scope by importing one of the following, based on
  your JSON library of choice.
  * `import org.http4s.argonaut._`
  * `import org.http4s.circe._`
  * `import org.http4s.json4s.jackson._`
  * `import org.http4s.json4s.native._`
* `AsyncHttpClient.apply` no longer takes a `bufferSize`.  It is made
  irrelevant by fs2-reactive-streams.
* `MultipartParser.parse` no longer takes a `headerLimit`, which was unused.
* Add `maxWaitQueueLimit` (default 256) and `maxConnectionsPerRequestKey`
  (default 10) to `PooledHttp1Client`.
* Remove private implicit `ExecutionContext` from `StreamApp`. This had been
  known to cause diverging implicit resolution that was hard to debug.
* Shift execution of the routing of the `HttpService` to the `ExecutionContext`
  provided by the `JettyBuilder` or `TomcatBuilder`. Previously, it only shifted
  the response task and stream. This was a regression from v0.16.
* Add two utility execution contexts. These may be used to increase throughput
  as the server builder's `ExecutionContext`. Blocking calls on routing may
  decrease fairness or even deadlock your service, so use at your own risk:
  * `org.http4s.util.execution.direct`
  * `org.http4s.util.execution.trampoline`
* Deprecate `EffectRequestSyntax` and `EffectResponseSyntax`. These were
  previously used to provide methods such as `.putHeaders` and `.withBody`
  on types `F[Request]` and `F[Response]`.  As an alternative:
  * Call `.map` or `.flatMap` on `F[Request]` and `F[Response]` to get access
    to all the same methods.
  * Variadic headers have been added to all the status code generators in
    `Http4sDsl[F]` and method generators in `import org.http4s.client._`.
    For example:
    * `POST(uri, urlForm, Header("Authorization", "Bearer s3cr3t"))`
    * ``Ok("This will have an html content type!", `Content-Type`(`text/html`))``
* Restate `HttpService[F]` as a `Kleisli[OptionT[F, ?], Request[F], Response[F]]`.
* Similarly, `AuthedService[F]` as a `Kleisli[OptionT[F, ?], AuthedRequest[F], Response[F]]`.
* `MaybeResponse` is removed, because the optionality is now expressed through
  the `OptionT` in `HttpService`. Instead of composing `HttpService` via a
  `Semigroup`, compose via a `SemigroupK`. Import `org.http4s.implicits._` to
  get a `SemigroupK[HttpService]`, and chain services as `s1 <+> s2`. We hope to
  remove the need for `org.http4s.implicits._` in a future version of cats with
  [issue 1428](https://github.com/typelevel/cats/issues/1428).
* The `Service` type alias is deprecated in favor of `Kleisli`.  It used to represent
  a partial application of the first type parameter, but since version 0.18, it is
  identical to `Kleisli.
* `HttpService.lift`, `AuthedService.lift` are deprecated in favor of `Kleisli.apply`.
* Remove `java.xml.bind` dependency from `GZip` middleware to avoid an
  extra module dependency in Java 9.
* Upgraded dependencies:
    * jawn-fs2-0.12.0-M2
    * log4s-1.4.0
* There is a classpath difference in log4s version between blaze and http4s in this
  milestone that will be remedied in M6. We believe these warnings are safe.

# v0.17.3 (2017-10-02)
* Shift execution of HttpService to the `ExecutionContext` provided by the
  `BlazeBuilder` when using HTTP/2. Previously, it only shifted the response
  task and body stream.

# v0.16.3 (2017-09-29)
* Fix `java.io.IOException: An invalid argument was supplied` on blaze-client
  for Windows when writing an empty sequence of `ByteBuffer`s.
* Set encoding of `captureWriter` to UTF-8 instead of the platform default.
* Dependency upgrades:
  * blaze-0.12.9

# v0.17.2 (2017-09-25)
* Remove private implicit strategy from `StreamApp`. This had been known to
  cause diverging implicit resolution that was hard to debug.
* Shift execution of HttpService to the `ExecutionContext` provided by the
  `BlazeBuilder`. Previously, it only shifted the response stream. This was a
  regression from 0.16.
* Split off http4s-parboiled2 module as `"org.http4s" %% "parboiled"`. There are
  no externally visible changes, but this simplifies and speeds the http4s
  build.

# v0.16.2 (2017-09-25)
* Dependency patch upgrades:
  * async-http-client-2.0.37
  * blaze-0.12.8: changes default number of selector threads to
    from `2 * cores + 1` to `max(4, cores + 1)`.
  * jetty-9.4.7.v20170914
  * tomcat-8.5.21
  * twirl-1.3.7

# v0.17.1 (2017-09-17)
* Fix bug where metrics were not captured in `Metrics` middleware.
* Pass `redactHeadersWhen` argument from `Logger` to `RequestLogger`
  and `ResponseLogger`.

# v0.16.1 (2017-09-17)
* Publish our fork of parboiled2 as http4s-parboiled2 module.  It's
  the exact same internal code as was in http4s-core, with no external
  dependencies. By publishing an extra module, we enable a
  `publishLocal` workflow.
* Charset fixes:
  * Deprecate `CharsetRange.isSatisfiedBy` in favor of
    and ```Accept-Charset`.isSatisfiedBy`` in favor of
    ```Accept-Charset`.satisfiedBy``.
  * Fix definition of `satisfiedBy` to respect priority of
    ```Charset`.*``.
  * Add `CharsetRange.matches`.
* ContentCoding fixes:
  * Deprecate `ContentCoding.satisfiedBy` and
    `ContentCoding.satisfies` in favor of ```Accept-Encoding`.satisfiedBy``.
  * Deprecate ```Accept-Encoding`.preferred``, which has no reasonable
    interpretation in the presence of splats.
  * Add ```Accept-Language`.qValue``.
  * Fix definition of `satisfiedBy` to respect priority of
    `ContentCoding.*`.
  * Add `ContentCoding.matches` and `ContentCoding.registered`.
  * Add `Arbitrary[ContentCoding]` and ```Arbitrary[`Accept-Encoding`]``
    instances.
* LanguageTag fixes:
  * Deprecate `LanguageTag.satisfiedBy` and
    `LanguageTag.satisfies` in favor of ```Accept-Language`.satisfiedBy``.
  * Fix definition of `satisfiedBy` to respect priority of
    `LanguageTag.*` and matches of a partial set of subtags.
  * Add `LanguageTag.matches`.
  * Deprecate `LanguageTag.withQuality` in favor of new
    `LanguageTag.withQValue`.
  * Deprecate ```Accept-Language`.preferred``, which has no reasonable
    interpretation in the presence of splats.
  * Add ```Accept-Language`.qValue``.
  * Add `Arbitrary[LanguageTag]` and ```Arbitrary[`Accept-Language`]``
    instances.

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

# v0.18.0-M1 (2017-08-24)

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
