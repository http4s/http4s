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

import cats.{Applicative, Functor, Monad}
import cats.data.{Kleisli, NonEmptyList}
import cats.syntax.all._
import org.http4s.Method.OPTIONS
import org.http4s.headers._
import org.http4s.util.{CaseInsensitiveString => CIString}
import org.log4s.getLogger
import scala.annotation.nowarn
import scala.concurrent.duration._

/** CORS middleware config options.
  * You can give an instance of this class to the CORS middleware,
  * to specify its behavior
  */
@deprecated(
  """Deficient. See https://github.com/http4s/http4s/security/advisories/GHSA-52cf-226f-rhr6.""",
  "0.21.27")
final case class CORSConfig(
    anyOrigin: Boolean,
    allowCredentials: Boolean,
    maxAge: Long,
    anyMethod: Boolean = true,
    allowedOrigins: String => Boolean = _ => false,
    allowedMethods: Option[Set[String]] = None,
    allowedHeaders: Option[Set[String]] = Set("Content-Type", "Authorization", "*").some,
    exposedHeaders: Option[Set[String]] = Set("*").some
)

/** Implements the CORS protocol.  The actual middleware is a [[CORSPolicy]],
  * which can be obtained via [[#policy]].
  *
  * @see [[CORSPolicy]]
  * @see [[https://fetch.spec.whatwg.org/#http-cors-protocol CORS protocol specification]]
  */
object CORS {
  private[CORS] val logger = getLogger

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
      Set(Method.GET, Method.HEAD, Method.PUT, Method.PATCH, Method.POST, Method.DELETE)),
    CORSPolicy.AllowHeaders.Reflect,
    CORSPolicy.MaxAge.Default
  )

  @deprecated(
    "Not the actual default CORS Vary heder, and will be removed from the public API.",
    "0.21.27")
  val defaultVaryHeader = Header("Vary", "Origin,Access-Control-Request-Method")

  @deprecated(
    "The default `CORSConfig` is insecure. See https://github.com/http4s/http4s/security/advisories/GHSA-52cf-226f-rhr6.",
    "0.21.27")
  def DefaultCORSConfig =
    CORSConfig(anyOrigin = true, allowCredentials = true, maxAge = 1.day.toSeconds)

  /** CORS middleware
    * This middleware provides clients with CORS information
    * based on information in CORS config.
    * Currently, you cannot make permissions depend on request details
    */
  @deprecated(
    "Depends on a deficient `CORSConfig`. See https://github.com/http4s/http4s/security/advisories/GHSA-52cf-226f-rhr6. If config.anyOrigin is true and config.allowCredentials is true, then the `Access-Control-Allow-Credentials` header will be suppressed starting with 0.21.27.",
    "0.21.27")
  @nowarn("cat=deprecation")
  def apply[F[_], G[_]](http: Http[F, G], config: CORSConfig = DefaultCORSConfig)(implicit
      F: Applicative[F]): Http[F, G] = {
    if (config.anyOrigin && config.allowCredentials)
      logger.warn(
        "Insecure CORS config detected: `anyOrigin=true` and `allowCredentials=true` are mutually exclusive. `Access-Control-Allow-Credentials` header will not be sent. Change either flag to false to remove this warning.")

    Kleisli { req =>
      // In the case of an options request we want to return a simple response with the correct Headers set.
      def createOptionsResponse(origin: Header, acrm: Header): Response[G] =
        corsHeaders(origin.value, acrm.value, isPreflight = true)(Response())

      def methodBasedHeader(isPreflight: Boolean) =
        if (isPreflight)
          config.allowedHeaders.map(headerFromStrings("Access-Control-Allow-Headers", _))
        else
          config.exposedHeaders.map(headerFromStrings("Access-Control-Expose-Headers", _))

      def varyHeader(response: Response[G]): Response[G] =
        response.headers.get(CIString("Vary")) match {
          case None => response.putHeaders(defaultVaryHeader)
          case _ => response
        }

      def allowCredentialsHeader(resp: Response[G]): Response[G] =
        if (!config.anyOrigin && config.allowCredentials)
          resp.putHeaders(Header("Access-Control-Allow-Credentials", "true"))
        else
          resp

      def corsHeaders(origin: String, acrm: String, isPreflight: Boolean)(
          resp: Response[G]): Response[G] = {
        val withMethodBasedHeader = methodBasedHeader(isPreflight)
          .fold(resp)(h => resp.putHeaders(h))

        varyHeader(allowCredentialsHeader(withMethodBasedHeader))
          .putHeaders(
            Header(
              "Access-Control-Allow-Methods",
              config.allowedMethods.fold(acrm)(_.mkString("", ", ", ""))),
            Header("Access-Control-Allow-Origin", origin),
            Header("Access-Control-Max-Age", config.maxAge.toString)
          )
      }

      def allowCORS(origin: Header, acrm: Header): Boolean =
        (config.anyOrigin, config.anyMethod, origin.value, acrm.value) match {
          case (true, true, _, _) => true
          case (true, false, _, acrm) =>
            config.allowedMethods.exists(_.contains(acrm))
          case (false, true, origin, _) => config.allowedOrigins(origin)
          case (false, false, origin, acrm) =>
            config.allowedMethods.exists(_.contains(acrm)) &&
              config.allowedOrigins(origin)
        }

      def headerFromStrings(headerName: String, values: Set[String]): Header =
        Header(headerName, values.mkString("", ", ", ""))

      (
        req.method,
        req.headers.get(Origin),
        req.headers.get(`Access-Control-Request-Method`)) match {
        case (OPTIONS, Some(origin), Some(acrm)) if allowCORS(origin, acrm) =>
          logger.debug(s"Serving OPTIONS with CORS headers for $acrm ${req.uri}")
          createOptionsResponse(origin, acrm).pure[F]
        case (_, Some(origin), _) =>
          if (allowCORS(origin, Header("Access-Control-Request-Method", req.method.renderString)))
            http(req).map { resp =>
              logger.debug(s"Adding CORS headers to ${req.method} ${req.uri}")
              corsHeaders(origin.value, req.method.renderString, isPreflight = false)(resp)
            }
          else {
            logger.debug(s"CORS headers were denied for ${req.method} ${req.uri}")
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
    "0.21.27")
  def httpRoutes[F[_]: Monad](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    apply(httpRoutes)

  @deprecated(
    """Hardcoded to an insecure config. See https://github.com/http4s/http4s/security/advisories/GHSA-52cf-226f-rhr6.""",
    "0.21.27")
  def httpApp[F[_]: Applicative](httpApp: HttpApp[F]): HttpApp[F] =
    apply(httpApp)
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
    maxAge: CORSPolicy.MaxAge
) {
  import CORSPolicy._

  def apply[F[_]: Applicative, G[_]](http: Http[F, G]): Http[F, G] =
    impl(http, Http.pure(Response(Status.Ok)))

  @deprecated("Does not return 200 on preflight requests. Use the Applicative version", "0.21.28")
  protected[CORSPolicy] def apply[F[_]: Functor, G[_]](http: Http[F, G]): Http[F, G] = {
    logger.warn(
      "This CORSPolicy does not return 200 on preflight requests. It's kept for binary compatibility, but it's buggy. If you see this, upgrade to v0.21.28 or greater.")
    impl(http, http)
  }

  def impl[F[_]: Functor, G[_]](http: Http[F, G], preflightResponder: Http[F, G]): Http[F, G] = {
    val allowCredentialsHeader: Option[Header] =
      allowCredentials match {
        case AllowCredentials.Allow =>
          CommonHeaders.someAllowCredentials
        case AllowCredentials.Deny =>
          None
      }

    val exposeHeadersHeader: Option[Header] =
      exposeHeaders match {
        case ExposeHeaders.All =>
          CommonHeaders.someExposeHeadersWildcard
        case ExposeHeaders.In(names) =>
          Header.Raw(`Access-Control-Expose-Headers`.name, names.mkString(", ")).some
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

    val someAllowHeadersSpecificHeader =
      allowHeaders match {
        case AllowHeaders.All | AllowHeaders.Reflect => None
        case AllowHeaders.In(headers) =>
          Header
            .Raw(`Access-Control-Allow-Headers`.name, headers.map(_.toString).mkString(", "))
            .some
      }

    val maxAgeHeader: Option[Header] =
      maxAge match {
        case MaxAge.Some(deltaSeconds) =>
          Header.Raw(`Access-Control-Max-Age`.name, deltaSeconds.toString).some
        case MaxAge.Default =>
          None
        case MaxAge.DisableCaching =>
          Header.Raw(`Access-Control-Max-Age`.name, "-1").some
      }

    val varyHeaderNonOptions: Option[Header] =
      allowOrigin match {
        case AllowOrigin.Match(_) =>
          Header.Raw(`Vary`.name, `Origin`.name.toString).some
        case _ =>
          None
      }

    val varyHeaderOptions: Option[Header] = {
      def origin = allowOrigin match {
        case AllowOrigin.All => Nil
        case AllowOrigin.Match(_) => List(`Origin`.name)
      }
      def methods = allowMethods match {
        case AllowMethods.All => Nil
        case AllowMethods.In(_) => List(`Access-Control-Request-Method`.name)
      }
      def headers = allowHeaders match {
        case AllowHeaders.All => Nil
        case AllowHeaders.In(_) | AllowHeaders.Reflect =>
          List(`Access-Control-Request-Headers`.name)
      }
      (origin ++ methods ++ headers) match {
        case Nil =>
          None
        case nonEmpty =>
          Header.Raw(`Vary`.name, nonEmpty.map(_.toString).mkString(", ")).some
      }
    }

    def dispatch(req: Request[G]) =
      req.headers.get(Origin) match {
        case Some(origin) =>
          req.method match {
            case Method.OPTIONS =>
              req.headers.get(`Access-Control-Request-Method`) match {
                case Some(methodRaw) =>
                  Method.fromString(methodRaw.value) match {
                    case Right(method) =>
                      val headers = req.headers.get(`Access-Control-Request-Headers`) match {
                        case Some(acrHeaders) =>
                          acrHeaders.value.split("\\s*,\\s*").map(CIString(_)).toSet
                        case None =>
                          Set.empty[CIString]
                      }
                      preflight(req, origin, method, headers)
                    case Left(_) =>
                      nonCors(req)
                  }
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
      val buff = List.newBuilder[Header]
      allowOriginHeader(origin).map { allowOrigin =>
        buff += allowOrigin
        allowCredentialsHeader.foreach(buff.+=)
        exposeHeadersHeader.foreach(buff.+=)
        buff
      }
      http(req).map(_.putHeaders(buff.result(): _*)).map(varyHeader(req.method))
    }

    def preflight(req: Request[G], origin: Origin, method: Method, headers: Set[CIString]) = {
      val buff = List.newBuilder[Header]
      (allowOriginHeader(origin), allowMethodsHeader(method), allowHeadersHeader(headers)).mapN {
        case (allowOrigin, allowMethods, allowHeaders) =>
          buff += allowOrigin
          allowCredentialsHeader.foreach(buff.+=)
          buff += allowMethods
          buff += allowHeaders
          maxAgeHeader.foreach(buff.+=)
      }
      preflightResponder(req).map(_.putHeaders(buff.result(): _*)).map(varyHeader(Method.OPTIONS))
    }

    def nonCors(req: Request[G]) =
      http(req).map(varyHeader(req.method))

    def allowOriginHeader(origin: Origin): Option[Header] =
      allowOrigin match {
        case AllowOrigin.All =>
          CommonHeaders.someAllowOriginWildcard
        case AllowOrigin.Match(p) =>
          if (p(origin))
            Header.Raw(`Access-Control-Allow-Origin`.name, origin.value).some
          else
            None
      }

    def allowMethodsHeader(method: Method): Option[Header] =
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

    def allowHeadersHeader(headers: Set[CIString]): Option[Header] =
      allowHeaders match {
        case AllowHeaders.All =>
          if (allowCredentials == AllowCredentials.Deny || headers === wildcardHeadersSet)
            CommonHeaders.someAllowHeadersWildcard
          else
            None
        case AllowHeaders.In(allowedHeaders) =>
          if ((headers -- allowedHeaders).isEmpty)
            someAllowHeadersSpecificHeader
          else
            None
        case AllowHeaders.Reflect =>
          Header.Raw(`Access-Control-Allow-Headers`.name, headers.mkString(", ")).some
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
            resp.headers.get(`Vary`) match {
              case None =>
                vary
              case Some(oldVary) =>
                Header.Raw(`Vary`.name, oldVary.value + ", " + vary.value)
            }
          )
        case None =>
          resp
      }

    if (allowOrigin == AllowOrigin.All && allowCredentials == AllowCredentials.Allow) {
      logger.warn(
        "Misconfiguration detected.  Sending `Access-Control-Allow-Origin: *` along with `Access-Control-Allow-Credentials: true` is blocked by Fetch-complaint user agents for security reasons.  Call `withAllowCredentials(false)`, or specify origins you trust with credential-tainted responses by calling `withAllowOriginHeader`, `withAllowOriginHost`, or `withAllowOriginHostCi`.")
      http
    } else
      Kleisli(dispatch)
  }

  private def copy(
      allowOrigin: AllowOrigin = allowOrigin,
      allowCredentials: AllowCredentials = allowCredentials,
      exposeHeaders: ExposeHeaders = exposeHeaders,
      allowMethods: AllowMethods = allowMethods,
      allowHeaders: AllowHeaders = allowHeaders,
      maxAge: MaxAge = maxAge
  ): CORSPolicy =
    new CORSPolicy(
      allowOrigin,
      allowCredentials,
      exposeHeaders,
      allowMethods,
      allowHeaders,
      maxAge
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
    * that constructing [[Origin.Host]] values is verbose.
    *
    * @see [[#withAllowOriginHost]]
    */
  def withAllowOriginHostCi(p: CIString => Boolean): CORSPolicy =
    withAllowOriginHost(p.compose(host => CIString(host.renderString)))

  /** Allow cross-origin requests to be made on a user's behalf using their credentials (cookies, TLS client certificates, and HTTP authentication entries) .  Sends an `Access-Control-Allow-Credentials: *`
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
      else MaxAge.Some(0L))

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
  private[CORSPolicy] val logger = getLogger

  private[middleware] object CommonHeaders {
    val someAllowOriginWildcard =
      Header.Raw(`Access-Control-Allow-Origin`.name, "*").some
    val someAllowCredentials =
      Header.Raw(`Access-Control-Allow-Credentials`.name, "true").some
    val someExposeHeadersWildcard =
      Header.Raw(`Access-Control-Expose-Headers`.name, "*").some
    val someAllowMethodsWildcard =
      Header.Raw(`Access-Control-Allow-Methods`.name, "*").some
    val someAllowHeadersWildcard =
      Header.Raw(`Access-Control-Allow-Headers`.name, "*").some
  }

  private[middleware] val wildcardMethod =
    Method.fromString("*").fold(throw _, identity)
  private[middleware] val wildcardHeadersSet =
    Set(CIString("*"))

  private[middleware] sealed trait AllowOrigin
  private[middleware] object AllowOrigin {
    case object All extends AllowOrigin
    case class Match(p: Origin => Boolean) extends AllowOrigin
  }

  private[middleware] sealed trait AllowCredentials
  private[middleware] object AllowCredentials {
    case object Allow extends AllowCredentials
    case object Deny extends AllowCredentials
  }

  private[middleware] sealed trait ExposeHeaders
  private[middleware] object ExposeHeaders {
    case object All extends ExposeHeaders
    case class In(names: Set[CIString]) extends ExposeHeaders
    case object None extends ExposeHeaders
  }

  private[middleware] sealed trait AllowMethods
  private[middleware] object AllowMethods {
    case object All extends AllowMethods
    case class In(names: Set[Method]) extends AllowMethods
  }

  private[middleware] sealed trait AllowHeaders
  private[middleware] object AllowHeaders {
    case object All extends AllowHeaders
    case class In(names: Set[CIString]) extends AllowHeaders
    case object Reflect extends AllowHeaders
  }

  private[middleware] sealed trait MaxAge
  private[middleware] object MaxAge {
    case class Some(seconds: Long) extends MaxAge
    case object Default extends MaxAge
    case object DisableCaching extends MaxAge
  }
}
