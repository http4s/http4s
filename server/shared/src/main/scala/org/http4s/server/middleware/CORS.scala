/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package server
package middleware

import cats.Applicative
import cats.Functor
import cats.Monad
import cats.data.Kleisli
import cats.data.NonEmptyList
import cats.syntax.all._
import org.http4s.Method.OPTIONS
import org.http4s.headers._
import org.http4s.syntax.header._
import org.typelevel.ci._
import org.typelevel.scalaccompat.annotation._

import scala.concurrent.duration._
import scala.util.hashing.MurmurHash3

/** CORS middleware config options.
  * You can give an instance of this class to the CORS middleware,
  * to specify its behavior
  */

@deprecated(
  """Deficient. See https://github.com/http4s/http4s/security/advisories/GHSA-52cf-226f-rhr6.""",
  "0.21.27",
)
final class CORSConfig private (
    val anyOrigin: Boolean,
    val allowCredentials: Boolean,
    val maxAge: FiniteDuration,
    val anyMethod: Boolean,
    val allowedOrigins: String => Boolean,
    val allowedMethods: Option[Set[Method]],
    val allowedHeaders: Option[Set[String]],
    val exposedHeaders: Option[Set[String]],
) {

  private def copy(
      anyOrigin: Boolean = anyOrigin,
      allowCredentials: Boolean = allowCredentials,
      maxAge: FiniteDuration = maxAge,
      anyMethod: Boolean = anyMethod,
      allowedOrigins: String => Boolean = allowedOrigins,
      allowedMethods: Option[Set[Method]] = allowedMethods,
      allowedHeaders: Option[Set[String]] = allowedHeaders,
      exposedHeaders: Option[Set[String]] = exposedHeaders,
  ) = new CORSConfig(
    anyOrigin,
    allowCredentials,
    maxAge,
    anyMethod,
    allowedOrigins,
    allowedMethods,
    allowedHeaders,
    exposedHeaders,
  )

  def withAnyOrigin(anyOrigin: Boolean): CORSConfig = copy(anyOrigin = anyOrigin)

  def withAllowCredentials(allowCredentials: Boolean): CORSConfig =
    copy(allowCredentials = allowCredentials)

  def withMaxAge(maxAge: FiniteDuration): CORSConfig = copy(maxAge = maxAge)

  def withAnyMethod(anyMethod: Boolean): CORSConfig = copy(anyMethod = anyMethod)

  def withAllowedOrigins(allowedOrigins: String => Boolean): CORSConfig =
    copy(allowedOrigins = allowedOrigins)

  def withAllowedMethods(allowedMethods: Option[Set[Method]]): CORSConfig =
    copy(allowedMethods = allowedMethods)

  def withAllowedHeaders(allowedHeaders: Option[Set[String]]): CORSConfig =
    copy(allowedHeaders = allowedHeaders)

  def withExposedHeaders(exposedHeaders: Option[Set[String]]): CORSConfig =
    copy(exposedHeaders = exposedHeaders)

  override def equals(x: Any): Boolean = x match {
    case config: CORSConfig =>
      anyOrigin === config.anyOrigin &&
      allowCredentials === config.allowCredentials &&
      maxAge === config.maxAge &&
      anyMethod === config.anyMethod &&
      allowedOrigins == config.allowedOrigins &&
      allowedMethods === config.allowedMethods &&
      allowedHeaders === config.allowedHeaders &&
      exposedHeaders === config.exposedHeaders
    case _ => false
  }

  override def hashCode(): Int = {
    var hash = CORSConfig.hashSeed
    hash = MurmurHash3.mix(hash, anyOrigin.##)
    hash = MurmurHash3.mix(hash, allowCredentials.##)
    hash = MurmurHash3.mix(hash, maxAge.##)
    hash = MurmurHash3.mix(hash, anyMethod.##)
    hash = MurmurHash3.mix(hash, allowedOrigins.##)
    hash = MurmurHash3.mix(hash, allowedMethods.##)
    hash = MurmurHash3.mix(hash, allowedHeaders.##)
    hash = MurmurHash3.mixLast(hash, exposedHeaders.##)
    hash
  }

  override def toString(): String =
    s"CORSConfig($anyOrigin,$allowCredentials,$maxAge,$anyMethod,$allowedOrigins,$allowedMethods,$allowedHeaders,$exposedHeaders)"
}

@deprecated(
  """Deficient. See https://github.com/http4s/http4s/security/advisories/GHSA-52cf-226f-rhr6.""",
  "0.21.27",
)
object CORSConfig {
  private val hashSeed = MurmurHash3.stringHash("CORSConfig")

  val default: CORSConfig = new CORSConfig(
    anyOrigin = true,
    allowCredentials = true,
    maxAge = 1.day,
    anyMethod = true,
    allowedOrigins = _ => false,
    allowedMethods = None,
    allowedHeaders = Set("Content-Type", "Authorization", "*").some,
    exposedHeaders = Set("*").some,
  )
}

/** Implements the CORS protocol.  The actual middleware is a [[CORSPolicy]],
  * which can be obtained via [[policy]].
  *
  * @see [[CORSPolicy]]
  * @see [[https://fetch.spec.whatwg.org/#http-cors-protocol CORS protocol specification]]
  */
object CORS {
  private[CORS] val logger = Platform.loggerFactory.getLogger

  /** The default CORS policy:
    * - Sends `Access-Control-Allow-Origin: *`
    * - Sends no `Access-Control-Allow-Credentials`
    * - Sends no `Access-Control-Expose-Headers`
    * - Sends `Access-Control-Allow-Methods: GET, HEAD, POST`
    * - Reflects request's `Access-Control-Request-Headers` as
    *   `Access-Control-Allow-Headers`
    * - Sends no `Access-Control-Max-Age`
    */
  val policy: CORSPolicy = new CORSPolicy(
    CORSPolicy.AllowOrigin.All,
    CORSPolicy.AllowCredentials.Deny,
    CORSPolicy.ExposeHeaders.None,
    CORSPolicy.AllowMethods.In(
      Set(Method.GET, Method.HEAD, Method.PUT, Method.PATCH, Method.POST, Method.DELETE)
    ),
    CORSPolicy.AllowHeaders.Reflect,
    CORSPolicy.MaxAge.Default,
  )

  @deprecated(
    "Not the actual default CORS Vary heder, and will be removed from the public API.",
    "0.21.27",
  )
  val defaultVaryHeader: Header.Raw = Header.Raw(ci"Vary", "Origin,Access-Control-Request-Method")

  @deprecated(
    "The default `CORSConfig` is insecure. See https://github.com/http4s/http4s/security/advisories/GHSA-52cf-226f-rhr6.",
    "0.21.27",
  )
  def DefaultCORSConfig: CORSConfig =
    CORSConfig.default.withAnyOrigin(true).withAllowCredentials(true).withMaxAge(1.day)

  /** CORS middleware
    * This middleware provides clients with CORS information
    * based on information in CORS config.
    * Currently, you cannot make permissions depend on request details
    */
  @deprecated(
    "Depends on a deficient `CORSConfig`. See https://github.com/http4s/http4s/security/advisories/GHSA-52cf-226f-rhr6. If config.anyOrigin is true and config.allowCredentials is true, then the `Access-Control-Allow-Credentials` header will be suppressed starting with 0.22.3.",
    "0.21.27",
  )
  @nowarn212("cat=deprecation")
  @nowarn3("cat=deprecation")
  def apply[F[_], G[_]](http: Http[F, G], config: CORSConfig = CORSConfig.default)(implicit
      F: Applicative[F]
  ): Http[F, G] = {
    if (config.anyOrigin && config.allowCredentials)
      logger
        .warn(
          "Insecure CORS config detected: `anyOrigin=true` and `allowCredentials=true` are mutually exclusive. `Access-Control-Allow-Credentials` header will not be sent. Change either flag to false to remove this warning."
        )
        .unsafeRunSync()
    Kleisli { req =>
      // In the case of an options request we want to return a simple response with the correct Headers set.
      def createOptionsResponse(
          origin: Origin,
          acrm: `Access-Control-Request-Method`,
      ): Response[G] =
        corsHeaders(origin, acrm.method, isPreflight = true)(Response())

      def methodBasedHeader(isPreflight: Boolean) =
        if (isPreflight)
          config.allowedHeaders.map(headerFromStrings("Access-Control-Allow-Headers", _))
        else
          config.exposedHeaders.map(headerFromStrings("Access-Control-Expose-Headers", _))

      def varyHeader(response: Response[G]): Response[G] =
        response.headers.get(ci"Vary") match {
          case None => response.putHeaders(defaultVaryHeader)
          case _ => response
        }

      def allowCredentialsHeader(resp: Response[G]): Response[G] =
        if (!config.anyOrigin && config.allowCredentials)
          resp.putHeaders("Access-Control-Allow-Credentials" -> "true")
        else
          resp

      def corsHeaders(origin: Origin, method: Method, isPreflight: Boolean)(
          resp: Response[G]
      ): Response[G] = {
        val withMethodBasedHeader = methodBasedHeader(isPreflight)
          .fold(resp)(h => resp.putHeaders(h))

        varyHeader(allowCredentialsHeader(withMethodBasedHeader))
          .putHeaders(
            `Access-Control-Allow-Methods`(config.allowedMethods.getOrElse(Set(method))),
            // TODO model me
            "Access-Control-Allow-Origin" -> origin.value,
            `Access-Control-Max-Age`.unsafeFromLong(config.maxAge.toSeconds.max(-1)),
          )
      }

      def allowCORS(origin: Origin, method: Method): Boolean = {
        def allowOrigin = config.anyOrigin || config.allowedOrigins(origin.value)
        def allowMethod = config.anyMethod || config.allowedMethods.exists(_.exists(_ === method))
        allowOrigin && allowMethod
      }

      def headerFromStrings(headerName: String, values: Set[String]): Header.Raw =
        Header.Raw(CIString(headerName), values.mkString("", ", ", ""))

      (
        req.method,
        req.headers.get[Origin],
        req.headers.get[`Access-Control-Request-Method`],
      ) match {
        case (OPTIONS, Some(origin), Some(acrm)) if allowCORS(origin, acrm.method) =>
          logger.debug(s"Serving OPTIONS with CORS headers for $acrm ${req.uri}").unsafeRunSync()
          createOptionsResponse(origin, acrm).pure[F]
        case (_, Some(origin), _) =>
          if (allowCORS(origin, req.method))
            http(req).map { resp =>
              logger.debug(s"Adding CORS headers to ${req.method} ${req.uri}").unsafeRunSync()
              corsHeaders(origin, req.method, isPreflight = false)(resp)
            }
          else {
            logger.debug(s"CORS headers were denied for ${req.method} ${req.uri}").unsafeRunSync()
            Response(status = Status.Forbidden).pure[F]
          }
        case _ =>
          // This request is out of scope for CORS
          http(req)
      }
    }
  }

  @deprecated(
    """Hardcoded to an insecure config. See https://github.com/http4s/http4s/security/advisories/GHSA-52cf-226f-rhr6.""",
    "0.21.27",
  )
  def httpRoutes[F[_]: Monad](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(httpRoutes, CORSConfig.default)

  @deprecated(
    """Hardcoded to an insecure config. See https://github.com/http4s/http4s/security/advisories/GHSA-52cf-226f-rhr6.""",
    "0.21.27",
  )
  def httpApp[F[_]: Applicative](httpApp: HttpApp[F]): HttpApp[F] =
    apply(httpApp, CORSConfig.default)
}

/** A middleware that applies the CORS protocol to any `Http` value.
  * Obtain a reference to a `CORSPolicy` via the [[CORS]] object,
  * which represents a default policy.
  *
  * Requests with an Origin header will receive the appropriate CORS
  * headers.  More headers are available for "pre-flight" requests,
  * those whose method is `OPTIONS` and has an
  * `Access-Control-Request-Method` header.
  *
  * Requests without the required headers, or requests that fail a
  * CORS origin, method, or headers check are passed through to the
  * underlying Http function, but do not receive any CORS headers in
  * the response.  The user agent will then block sharing the resource
  * across origins according to the CORS protocol.
  */
sealed class CORSPolicy(
    allowOrigin: CORSPolicy.AllowOrigin,
    allowCredentials: CORSPolicy.AllowCredentials,
    exposeHeaders: CORSPolicy.ExposeHeaders,
    allowMethods: CORSPolicy.AllowMethods,
    allowHeaders: CORSPolicy.AllowHeaders,
    maxAge: CORSPolicy.MaxAge,
) {
  import CORSPolicy._

  def apply[F[_]: Applicative, G[_]](http: Http[F, G]): Http[F, G] =
    applicatively(http)

  // Hack because httpRoutes and httpApp convenience constructors want this
  private def applicatively[F[_]: Applicative, G[_]](http: Http[F, G]): Http[F, G] =
    impl(http, Http.pure(Response(Status.Ok)))

  @deprecated("Does not return 200 on preflight requests. Use the Applicative version", "0.21.28")
  protected[CORSPolicy] def apply[F[_]: Functor, G[_]](http: Http[F, G]): Http[F, G] = {
    logger
      .warn(
        "This CORSPolicy does not return 200 on preflight requests. It's kept for binary compatibility, but it's buggy. If you see this, upgrade to v0.23.3 or greater."
      )
      .unsafeRunSync()
    impl(http, http)
  }

  def impl[F[_]: Functor, G[_]](http: Http[F, G], preflightResponder: Http[F, G]): Http[F, G] = {

    val allowCredentialsHeader =
      allowCredentials match {
        case AllowCredentials.Allow =>
          CommonHeaders.someAllowCredentials
        case AllowCredentials.Deny =>
          None
      }

    val exposeHeadersHeader =
      exposeHeaders match {
        case ExposeHeaders.All =>
          CommonHeaders.someExposeHeadersWildcard
        case ExposeHeaders.In(names) =>
          Header.Raw(Header[`Access-Control-Expose-Headers`].name, names.mkString(", ")).some
        case ExposeHeaders.None =>
          None
      }

    val someAllowMethodsSpecificHeader =
      allowMethods match {
        case AllowMethods.All => None
        case AllowMethods.In(methods) =>
          Header
            .Raw(`Access-Control-Allow-Methods`.name, methods.map(_.renderString).mkString(", "))
            .some
      }

    val maxAgeHeader =
      maxAge match {
        case MaxAge.Some(deltaSeconds) =>
          Header.Raw(`Access-Control-Max-Age`.name, deltaSeconds.toString).some
        case MaxAge.Default =>
          None
        case MaxAge.DisableCaching =>
          Header.Raw(`Access-Control-Max-Age`.name, "-1").some
      }

    val varyHeaderNonOptions =
      allowOrigin match {
        case AllowOrigin.Match(_) =>
          Header.Raw(ci"Vary", Header[Origin].name.toString).some
        case _ =>
          None
      }

    val varyHeaderOptions = {
      def origin = allowOrigin match {
        case AllowOrigin.All => Nil
        case AllowOrigin.Match(_) => List(Header[Origin].name)
      }
      def methods = allowMethods match {
        case AllowMethods.All => Nil
        case AllowMethods.In(_) => List(Header[`Access-Control-Request-Method`].name)
      }
      def headers = allowHeaders match {
        case AllowHeaders.All | AllowHeaders.Static(_) => Nil
        case AllowHeaders.In(_) | AllowHeaders.Reflect =>
          List(ci"Access-Control-Request-Headers")
      }
      (origin ++ methods ++ headers) match {
        case Nil =>
          None
        case nonEmpty =>
          Header.Raw(ci"Vary", nonEmpty.map(_.toString).mkString(", ")).some
      }
    }

    def dispatch(req: Request[G]) =
      req.headers.get[Origin] match {
        case Some(origin) =>
          req.method match {
            case Method.OPTIONS =>
              req.headers.get[`Access-Control-Request-Method`] match {
                case Some(acrm) =>
                  val headers = req.headers.get(ci"Access-Control-Request-Headers") match {
                    case Some(acrHeaders) =>
                      acrHeaders.map(_.value.split("\\s*,\\s*").map(CIString(_)).toSet).fold
                    case None =>
                      Set.empty[CIString]
                  }
                  preflight(req, origin, acrm.method, headers)
                case None =>
                  nonPreflight(req, origin)
              }
            case _ =>
              nonPreflight(req, origin)
          }
        case None =>
          nonCors(req)
      }

    def nonPreflight(req: Request[G], origin: Origin) = {
      val buff = List.newBuilder[Header.Raw]
      allowOriginHeader(origin).map { allowOrigin =>
        buff += allowOrigin
        allowCredentialsHeader.foreach(buff.+=)
        exposeHeadersHeader.foreach(buff.+=)
        buff
      }
      http(req)
        .map(_.putHeaders(buff.result().map(Header.ToRaw.rawToRaw): _*))
        .map(varyHeader(req.method))
    }

    def preflight(req: Request[G], origin: Origin, method: Method, headers: Set[CIString]) = {
      val buff = List.newBuilder[Header.Raw]
      (allowOriginHeader(origin), allowMethodsHeader(method), allowHeadersHeader(headers)).mapN {
        case (allowOrigin, allowMethods, allowHeaders) =>
          buff += allowOrigin
          allowCredentialsHeader.foreach(buff.+=)
          buff += allowMethods
          buff += allowHeaders
          maxAgeHeader.foreach(buff.+=)
      }
      preflightResponder(req)
        .map(_.putHeaders(buff.result().map(Header.ToRaw.rawToRaw): _*))
        .map(varyHeader(Method.OPTIONS))
    }

    def nonCors(req: Request[G]) =
      http(req).map(varyHeader(req.method))

    def allowOriginHeader(origin: Origin) =
      allowOrigin match {
        case AllowOrigin.All =>
          CommonHeaders.someAllowOriginWildcard
        case AllowOrigin.Match(p) =>
          if (p(origin))
            Header.Raw(ci"Access-Control-Allow-Origin", origin.value).some
          else
            None
      }

    def allowMethodsHeader(method: Method) =
      allowMethods match {
        case AllowMethods.All =>
          if (allowCredentials == AllowCredentials.Deny || method === wildcardMethod)
            CommonHeaders.someAllowMethodsWildcard
          else
            None
        case AllowMethods.In(methods) =>
          if (methods.contains(method))
            someAllowMethodsSpecificHeader
          else
            None
      }

    def someAllowHeadersHeader(headers: Set[CIString]) =
      Header
        .Raw(Header[`Access-Control-Allow-Headers`].name, headers.map(_.toString).mkString(", "))
        .some

    def allowHeadersHeader(requestHeaders: Set[CIString]) =
      allowHeaders match {
        case AllowHeaders.All =>
          if (allowCredentials == AllowCredentials.Deny || requestHeaders === wildcardHeadersSet)
            CommonHeaders.someAllowHeadersWildcard
          else
            None
        case AllowHeaders.Static(allowedHeaders) =>
          someAllowHeadersHeader(allowedHeaders)
        case AllowHeaders.In(allowedHeaders) =>
          if ((requestHeaders -- allowedHeaders).isEmpty)
            someAllowHeadersHeader(allowedHeaders)
          else
            None
        case AllowHeaders.Reflect =>
          someAllowHeadersHeader(requestHeaders)
      }

    // Working around the poor model in 0.21.  If we just add a Vary
    // header, it clobbers the existing one, because it's not properly
    // flagged as recurring.  This doesn't need to be special when the
    // Vary model is fixed.
    def varyHeader(method: Method)(resp: Response[G]) =
      (method match {
        case Method.OPTIONS => varyHeaderOptions
        case _ => varyHeaderNonOptions
      }) match {
        case Some(vary) =>
          resp.putHeaders(
            resp.headers.get(ci"Vary") match {
              case None =>
                vary
              case Some(oldVary) =>
                Header.Raw(ci"Vary", oldVary.map(_.value).toList.mkString(", ") + ", " + vary.value)
            }
          )
        case _ =>
          resp
      }

    if (allowOrigin == AllowOrigin.All && allowCredentials == AllowCredentials.Allow) {
      logger
        .warn(
          "CORS disabled due to insecure config prohibited by spec. Call withCredentials(false) to avoid sharing credential-tainted responses with arbitrary origins, or call withAllowOrigin* method to be explicit who you trust with credential-tainted responses."
        )
        .unsafeRunSync()
      http
    } else
      Kleisli(dispatch)
  }

  def httpRoutes[F[_]: Monad](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    applicatively(httpRoutes)

  def httpApp[F[_]: Applicative](httpApp: HttpApp[F]): HttpApp[F] =
    applicatively(httpApp)

  private def copy(
      allowOrigin: AllowOrigin = allowOrigin,
      allowCredentials: AllowCredentials = allowCredentials,
      exposeHeaders: ExposeHeaders = exposeHeaders,
      allowMethods: AllowMethods = allowMethods,
      allowHeaders: AllowHeaders = allowHeaders,
      maxAge: MaxAge = maxAge,
  ): CORSPolicy =
    new CORSPolicy(
      allowOrigin,
      allowCredentials,
      exposeHeaders,
      allowMethods,
      allowHeaders,
      maxAge,
    )

  /** Allow CORS requests from any origin with an
    * `Access-Control-Allow-Origin` of `*`.
    */
  def withAllowOriginAll: CORSPolicy =
    copy(AllowOrigin.All)

  /** Allow requests from any origin matching the predicate `p`.  On
    * matching requests, the request origin is reflected as the
    * `Access-Control-Allow-Origin` header.
    *
    * The Origin header contains some arcane settings, like multiple
    * origins, or a `null` origin. `withAllowOriginHost` is generally
    * more convenient.
    */
  def withAllowOriginHeader(p: Origin => Boolean): CORSPolicy =
    copy(AllowOrigin.Match(p))

  /** Allow requests from any origin host matching the predicate `p`.
    * The origin host is the first value in the request's `Origin`
    * header, if not `null` header, unless it is `null`.  Examples:
    *
    * - `Origin: http://www.example.com` => `http://www.example.com`
    * - `Origin: http://www.example.com, http://example.net` =>
    *   `http://www.example.com`
    * - `Origin: null` => always false
    *
    * A `Set[Origin.Host]` is often a good choice here, but a predicate is
    * offered to support more advanced matching.
    */
  def withAllowOriginHost(p: Origin.Host => Boolean): CORSPolicy =
    withAllowOriginHeader(_ match {
      case Origin.HostList(NonEmptyList(h, _)) => p(h)
      case Origin.Null => false
    })

  /** Allow requests from any origin host whose case-insensitive
    * rendering matches predicate `p`.  A concession to the fact
    * that constructing [[org.http4s.headers.Origin.Host]] values is verbose.
    *
    * @see [[withAllowOriginHost]]
    */
  def withAllowOriginHostCi(p: CIString => Boolean): CORSPolicy =
    withAllowOriginHost(p.compose(host => CIString(host.renderString)))

  /** Allow credentials.  Sends an `Access-Control-Allow-Credentials: *`
    * on valid CORS requests if true, and omits the header if false.
    *
    * For security purposes, it is an invalid per the Fetch Living Standard
    * that defines CORS to set this to `true` when any origin is allowed.
    */
  def withAllowCredentials(b: Boolean): CORSPolicy =
    copy(allowCredentials = if (b) AllowCredentials.Allow else AllowCredentials.Deny)

  /** Exposes all response headers to the origin.
    *
    * Sends an `Access-Control-Expose-Headers: *` header on valid
    * CORS non-preflight requests.
    */
  def withExposeHeadersAll: CORSPolicy =
    copy(exposeHeaders = ExposeHeaders.All)

  /** Exposes specific response headers to the origin.  These are in
    * addition to CORS-safelisted response headers.
    *
    * Sends an `Access-Control-Expose-Headers` header with names as
    * a comma-delimited string on valid CORS non-preflight requests.
    */
  def withExposeHeadersIn(names: Set[CIString]): CORSPolicy =
    copy(exposeHeaders = ExposeHeaders.In(names))

  /** Exposes no response headers to the origin beyond the
    * CORS-safelisted response headers.
    *
    * Sends an `Access-Control-Expose-Headers` header with names as
    * a comma-delimited string on valid CORS non-preflight requests.
    */
  def withExposeHeadersNone: CORSPolicy =
    copy(exposeHeaders = ExposeHeaders.None)

  /** Allows CORS requests with any method if credentials are not
    * allowed.  If credentials are allowed, allows requests with
    * a literal method of `*`, which is almost certainly not what
    * you mean, but per spec.
    *
    * Sends an `Access-Control-Allow-Headers: *` header on valid
    * CORS preflight requests.
    */
  def withAllowMethodsAll: CORSPolicy =
    copy(allowMethods = AllowMethods.All)

  /** Allows CORS requests with any of the specified methods
    * allowed.
    *
    * Preflight requests must send a matching
    * `Access-Control-Request-Method` header to receive a CORS
    * response.
    *
    * Sends an `Access-Control-Allow-Headers` header with the
    * specified headers on valid CORS preflight requests.
    */
  def withAllowMethodsIn(methods: Set[Method]): CORSPolicy =
    copy(allowMethods = AllowMethods.In(methods))

  /** Allows CORS requests with any headers if credentials are not
    * allowed.  If credentials are allowed, allows requests with a
    * literal header name of `*`, which is almost certainly not what
    * you mean, but per spec.
    *
    * Sends an `Access-Control-Allow-Headers: *` header on valid
    * CORS preflight requests.
    */
  def withAllowHeadersAll: CORSPolicy =
    copy(allowHeaders = AllowHeaders.All)

  /** Allows CORS requests whose request headers are a subset of the
    * headers enumerated here, or are CORS-safelisted.
    *
    * If preflight requests send an `Access-Control-Request-Headers`
    * header, its value must be a subset of those passed here.
    *
    * Sends an `Access-Control-Allow-Headers` header with the
    * specified headers on valid CORS preflight requests.
    */
  def withAllowHeadersIn(headers: Set[CIString]): CORSPolicy =
    copy(allowHeaders = AllowHeaders.In(headers))

  /** Reflects the `Access-Control-Request-Headers` back as
    * `Access-Control-Allow-Headers` on preflight requests. This is
    * most useful when credentials are allowed and a wildcard for
    * `Access-Control-Allow-Headers` would be treated literally.
    *
    * Sends an `Access-Control-Allow-Headers` header with the
    * specified headers on valid CORS preflight requests.
    */
  def withAllowHeadersReflect: CORSPolicy =
    copy(allowHeaders = AllowHeaders.Reflect)

  /** Returns a static value in `Access-Control-Allow-Headers` on
    * preflight requests consisting of the supplied headers.
    *
    * Sends an `Access-Control-Allow-Headers` header with the
    * specified headers on valid CORS preflight requests.
    */
  def withAllowHeadersStatic(headers: Set[CIString]): CORSPolicy =
    copy(allowHeaders = AllowHeaders.Static(headers))

  /** Sets the duration the results can be cached.  The duration is
    * truncated to seconds.  A negative value results in a cache
    * duration of zero.
    *
    * Sends an `Access-Control-Max-Age` header with the duration
    * in seconds on preflight requests.
    */
  def withMaxAge(duration: FiniteDuration): CORSPolicy =
    copy(maxAge =
      if (duration >= Duration.Zero) MaxAge.Some(duration.toSeconds)
      else MaxAge.Some(0L)
    )

  /** Sets the duration the results can be cached to the user agent's
    * default. This suppresses the `Access-Control-Max-Age` header.
    */
  def withMaxAgeDefault: CORSPolicy =
    copy(maxAge = MaxAge.Default)

  /** Instructs the client to not cache any preflight results.
    *
    * Sends an `Access-Control-Max-Age: -1` header
    */
  def withMaxAgeDisableCaching: CORSPolicy =
    copy(maxAge = MaxAge.DisableCaching)
}

object CORSPolicy {
  private val logger = Platform.loggerFactory.getLogger

  private object CommonHeaders {
    val someAllowOriginWildcard: Option[Header.Raw] =
      Header.Raw(ci"Access-Control-Allow-Origin", "*").some
    val someAllowCredentials: Option[Header.Raw] =
      Header.Raw(Header[`Access-Control-Allow-Credentials`].name, "true").some
    val someExposeHeadersWildcard: Option[Header.Raw] =
      Header.Raw(Header[`Access-Control-Expose-Headers`].name, "*").some
    val someAllowMethodsWildcard: Option[Header.Raw] =
      Header.Raw(`Access-Control-Allow-Methods`.name, "*").some
    val someAllowHeadersWildcard: Option[Header.Raw] =
      Header.Raw(Header[`Access-Control-Allow-Headers`].name, "*").some
  }

  private[middleware] val wildcardMethod =
    Method.fromString("*").fold(throw _, identity)
  private[middleware] val wildcardHeadersSet =
    Set(CIString("*"))

  private[middleware] sealed trait AllowOrigin
  private[middleware] object AllowOrigin {
    case object All extends AllowOrigin
    final case class Match(p: Origin => Boolean) extends AllowOrigin
  }

  private[middleware] sealed trait AllowCredentials
  private[middleware] object AllowCredentials {
    case object Allow extends AllowCredentials
    case object Deny extends AllowCredentials
  }

  private[middleware] sealed trait ExposeHeaders
  private[middleware] object ExposeHeaders {
    case object All extends ExposeHeaders
    final case class In(names: Set[CIString]) extends ExposeHeaders
    case object None extends ExposeHeaders
  }

  private[middleware] sealed trait AllowMethods
  private[middleware] object AllowMethods {
    case object All extends AllowMethods
    final case class In(names: Set[Method]) extends AllowMethods
  }

  private[middleware] sealed trait AllowHeaders
  private[middleware] object AllowHeaders {
    case object All extends AllowHeaders
    final case class In(names: Set[CIString]) extends AllowHeaders
    case object Reflect extends AllowHeaders
    final case class Static(names: Set[CIString]) extends AllowHeaders
  }

  private[middleware] sealed trait MaxAge
  private[middleware] object MaxAge {
    final case class Some(seconds: Long) extends MaxAge
    case object Default extends MaxAge
    case object DisableCaching extends MaxAge
  }
}
