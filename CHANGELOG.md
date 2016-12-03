# v0.15.0-SNASPHOT (unreleased)
* Added `Client.fromHttpService` to assist with testing.
* Make all case classes final where possible, sealed where not.
* Codec for Server Sent Events (SSE)
* Added JSONP middleware
* Improve Expires header to more easily build the header and support parsing of the header
* Replce lazy `Raw.parsed` field with a siple null check
* Added support for Zipkin headers
* Upgrade to json4s-3.4.0

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
* Retry middleware now only retries requests with idempotent methods and pure bodies and appropriate status codes
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
* Rewrite client connection pool to support  maximum concurrent connections instead of maximum idle connections.
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

# v0.8.5 (2015-08-26)
* Preserve original headers when appending as opposed to quoting.
* Upgrade to jawn-0.8.3 to avoid transitive dependency on GPL2 jmh

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

* Change HttpService form a `PartialFunction[Request,Task[Response]]` to `Service[Request, Response]`,
  a type that encapsulates a `Request => Task[Option[Response]]`
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
