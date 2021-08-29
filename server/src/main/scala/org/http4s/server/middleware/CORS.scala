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

object CORS {
  private[CORS] val logger = getLogger

  private[CORS] object CommonHeaders {
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

  private[CORS] val wildcardMethod =
    Method.fromString("*").fold(throw _, identity)
  private[CORS] val wildcardHeadersSet =
    Set(CIString("*"))

  private[CORS] sealed trait AllowOrigin
  private[CORS] object AllowOrigin {
    case object All extends AllowOrigin
    case class Match(p: Origin => Boolean) extends AllowOrigin
  }

  private[CORS] sealed trait AllowCredentials
  private[CORS] object AllowCredentials {
    case object Allow extends AllowCredentials
    case object Deny extends AllowCredentials
  }

  private[CORS] sealed trait ExposeHeaders
  private[CORS] object ExposeHeaders {
    case object All extends ExposeHeaders
    case class In(names: Set[CIString]) extends ExposeHeaders
    case object None extends ExposeHeaders
  }

  private[CORS] sealed trait AllowMethods
  private[CORS] object AllowMethods {
    case object All extends AllowMethods
    case class In(names: Set[Method]) extends AllowMethods
  }

  private[CORS] sealed trait AllowHeaders
  private[CORS] object AllowHeaders {
    case object All extends AllowHeaders
    case class In(names: Set[CIString]) extends AllowHeaders
    case object Reflect extends AllowHeaders
  }

  private[CORS] sealed trait MaxAge
  private[CORS] object MaxAge {
    case class Some(seconds: Long) extends MaxAge
    case object Default extends MaxAge
    case object DisableCaching extends MaxAge
  }

  final class Policy private[CORS] (
      allowOrigin: AllowOrigin,
      allowCredentials: AllowCredentials,
      exposeHeaders: ExposeHeaders,
      allowMethods: AllowMethods,
      allowHeaders: AllowHeaders,
      maxAge: MaxAge
  ) {
    def apply[F[_]: Functor, G[_]](http: Http[F, G]): Http[F, G] = {

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

      def corsHeaders(req: Request[G]) =
        req.headers.get(Origin) match {
          case Some(origin) =>
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
                    preflightHeaders(origin, method, headers)
                  case Left(_) =>
                    Nil
                }
              case _ =>
                nonPreflightHeaders(origin)
            }
          case None =>
            Nil
        }

      def varyHeader(method: Method) =
        method match {
          case Method.OPTIONS => varyHeaderOptions
          case _ => varyHeaderNonOptions
        }

      def dispatch(req: Request[G]) = {
        val cHeaders = corsHeaders(req)
        val vHeader = varyHeader(req.method)
        val headers = vHeader.fold(cHeaders)(_ :: cHeaders)
        val resp = http(req)
        if (headers.nonEmpty)
          resp.map(_.putHeaders(headers: _*))
        else
          resp
      }

      def nonPreflightHeaders(origin: Origin) = {
        val buff = List.newBuilder[Header]
        allowOriginHeader(origin).map { allowOrigin =>
          buff += allowOrigin
          allowCredentialsHeader.foreach(buff.+=)
          exposeHeadersHeader.foreach(buff.+=)
          buff
        }
        buff.result()
      }

      def preflightHeaders(origin: Origin, method: Method, headers: Set[CIString]) = {
        val buff = List.newBuilder[Header]
        (allowOriginHeader(origin), allowMethodsHeader(method), allowHeadersHeader(headers)).mapN {
          case (allowOrigin, allowMethods, allowHeaders) =>
            buff += allowOrigin
            allowCredentialsHeader.foreach(buff.+=)
            buff += allowMethods
            buff += allowHeaders
            maxAgeHeader.foreach(buff.+=)
            buff.result()
        }
        buff.result()
      }

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

      if (allowOrigin == AllowOrigin.All && allowCredentials == AllowCredentials.Allow) {
        logger.warn(
          "CORS disabled due to insecure config prohibited by spec. Call withCredentials(false) to avoid sharing credential-tainted responses with arbitrary origins, or call withAllowOrigin* method to be explicit who you trust with credential-tainted responses.")
        http
      } else
        Kleisli(dispatch)
    }

    def copy(
        allowOrigin: AllowOrigin = allowOrigin,
        allowCredentials: AllowCredentials = allowCredentials,
        exposeHeaders: ExposeHeaders = exposeHeaders,
        allowMethods: AllowMethods = allowMethods,
        allowHeaders: AllowHeaders = allowHeaders,
        maxAge: MaxAge = maxAge
    ): Policy =
      new Policy(
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
    def withAllowOriginAll: Policy =
      copy(AllowOrigin.All)

    /** Allow requests from any origin matching the predicate `p`.  On
      * matching requests, the request origin is reflected as the
      * `Access-Control-Allow-Origin` header.
      *
      * The Origin header contains some arcane settings, like multiple
      * origins, or a `null` origin. `withAllowOriginHost` is generally
      * more convenient.
      */
    def withAllowOriginHeader(p: Origin => Boolean): Policy =
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
    def withAllowOriginHost(p: Origin.Host => Boolean): Policy =
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
    def withAllowOriginHostCi(p: CIString => Boolean): Policy =
      withAllowOriginHost(p.compose(host => CIString(host.renderString)))

    /** Allow credentials.  Sends an `Access-Control-Allow-Credentials: *`
      * on valid CORS requests if true, and omits the header if false.
      *
      * For security purposes, it is an invalid per the Fetch Living Standard
      * that defines CORS to set this to `true` when any origin is allowed.
      */
    def withAllowCredentials(b: Boolean): Policy =
      copy(allowCredentials = if (b) AllowCredentials.Allow else AllowCredentials.Deny)

    /** Exposes all response headers to the origin.
      *
      * Sends an `Access-Control-Expose-Headers: *` header on valid
      * CORS non-preflight requests.
      */
    def withExposeHeadersAll: Policy =
      copy(exposeHeaders = ExposeHeaders.All)

    /** Exposes specific response headers to the origin.  These are in
      * addition to CORS-safelisted response headers.
      *
      * Sends an `Access-Control-Expose-Headers` header with names as
      * a comma-delimited string on valid CORS non-preflight requests.
      */
    def withExposeHeadersIn(names: Set[CIString]): Policy =
      copy(exposeHeaders = ExposeHeaders.In(names))

    /** Exposes no response headers to the origin beyond the
      * CORS-safelisted response headers.
      *
      * Sends an `Access-Control-Expose-Headers` header with names as
      * a comma-delimited string on valid CORS non-preflight requests.
      */
    def withExposeHeadersNone: Policy =
      copy(exposeHeaders = ExposeHeaders.None)

    /** Allows CORS requests with any method if credentials are not
      * allowed.  If credentials are allowed, allows requests with
      * a literal method of `*`, which is almost certainly not what
      * you mean, but per spec.
      *
      * Sends an `Access-Control-Allow-Headers: *` header on valid
      * CORS preflight requests.
      */
    def withAllowMethodsAll: Policy =
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
    def withAllowMethodsIn(methods: Set[Method]): Policy =
      copy(allowMethods = AllowMethods.In(methods))

    /** Allows CORS requests with any headers if credentials are not
      * allowed.  If credentials are allowed, allows requests with a
      * literal header name of `*`, which is almost certainly not what
      * you mean, but per spec.
      *
      * Sends an `Access-Control-Allow-Headers: *` header on valid
      * CORS preflight requests.
      */
    def withAllowHeadersAll: Policy =
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
    def withAllowHeadersIn(headers: Set[CIString]): Policy =
      copy(allowHeaders = AllowHeaders.In(headers))

    /** Reflects the `Access-Control-Request-Headers` back as
      * `Access-Control-Allow-Headers` on preflight requests. This is
      * most useful when credentials are allowed and a wildcard for
      * `Access-Control-Allow-Headers` would be treated literally.
      *
      * Sends an `Access-Control-Allow-Headers` header with the
      * specified headers on valid CORS preflight requests.
      */
    def withAllowHeadersReflect: Policy =
      copy(allowHeaders = AllowHeaders.Reflect)

    /** Sets the duration the results can be cached.  The duration is
      * truncated to seconds.  A negative value results in a cache
      * duration of zero.
      *
      * Sends an `Access-Control-Max-Age` header with the duration
      * in seconds on preflight requests.
      */
    def withMaxAge(duration: FiniteDuration): Policy =
      copy(maxAge =
        if (duration >= Duration.Zero) MaxAge.Some(duration.toSeconds)
        else MaxAge.Some(0L))

    /** Sets the duration the results can be cached to the user agent's
      * default. This suppresses the `Access-Control-Max-Age` header.
      */
    def withMaxAgeDefault: Policy =
      copy(maxAge = MaxAge.Default)

    /** Instructs the client to not cache any preflight results.
      *
      * Sends an `Access-Control-Max-Age: -1` header
      */
    def withMaxAgeDisableCaching: Policy =
      copy(maxAge = MaxAge.DisableCaching)
  }

  private val defaultPolicy: Policy = new Policy(
    AllowOrigin.Match(Function.const(false)),
    AllowCredentials.Deny,
    ExposeHeaders.None,
    AllowMethods.In(
      Set(Method.GET, Method.HEAD, Method.PUT, Method.PATCH, Method.POST, Method.DELETE)),
    AllowHeaders.Reflect,
    MaxAge.Default
  )

  /** A CORS policy that allows requests from any origin.
    *
    * @see {Policy#withAllowAnyOrigin}
    */
  val withAllowOriginAll: Policy =
    defaultPolicy.withAllowOriginAll

  /** A CORS policy that allows requests from any origin header matching
    * predicate `p`.  You probably want [[#withAllowOriginHost]]
    *
    * @see [[#withAllowOriginHost]]
    * @see [[Policy#withAllowOriginHeader]]
    */
  def withAllowOriginHeader(p: Origin => Boolean): Policy =
    defaultPolicy.withAllowOriginHeader(p)

  /** A CORS policy that allows requests from any origin host matching
    * predicate `p`.
    *
    * @see [[Policy#withAllowOriginHost]]
    */
  def withAllowOriginHost(p: Origin.Host => Boolean): Policy =
    defaultPolicy.withAllowOriginHost(p)

  /** A CORS policy that allows requests from any origin host whose
    * case-insensitive rendering matches predicate `p`.  A concession
    * to the fact that constructing [[Origin.Host]] values is verbose.
    *
    * @see [[Policy#withAllowOriginCi]]
    */
  def withAllowOriginHostCi(p: CIString => Boolean): Policy =
    defaultPolicy.withAllowOriginHostCi(p)

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
    "Depends on a deficient `CORSConfig`. See https://github.com/http4s/http4s/security/advisories/GHSA-52cf-226f-rhr6.",
    "0.21.27")
  @nowarn("cat=deprecation")
  def apply[F[_], G[_]](http: Http[F, G], config: CORSConfig = DefaultCORSConfig)(implicit
      F: Applicative[F]): Http[F, G] =
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

      def corsHeaders(origin: String, acrm: String, isPreflight: Boolean)(
          resp: Response[G]): Response[G] = {
        val withMethodBasedHeader = methodBasedHeader(isPreflight)
          .fold(resp)(h => resp.putHeaders(h))

        varyHeader(withMethodBasedHeader)
          .putHeaders(
            Header("Access-Control-Allow-Credentials", config.allowCredentials.toString()),
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
