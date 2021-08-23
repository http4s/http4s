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
    val allowOriginWildcard =
      Header.Raw(`Access-Control-Allow-Origin`.name, "*")
    val someAllowCredentials =
      Header.Raw(`Access-Control-Allow-Credentials`.name, "true").some
    val someExposeHeadersWildcard =
      Header.Raw(`Access-Control-Expose-Headers`.name, "*").some
  }

  private[CORS] sealed trait AllowOrigin
  private[CORS] object AllowOrigin {
    case object Any extends AllowOrigin
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

  final class Policy private[CORS] (
      allowOrigin: AllowOrigin,
      allowCredentials: AllowCredentials,
      exposeHeaders: ExposeHeaders) {
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

      def dispatch(req: Request[G]) =
        req.headers.get(Origin) match {
          case Some(origin) =>
            handleCors(req, origin)
          case None =>
            http(req)
        }

      def handleCors(req: Request[G], origin: Origin) = {
        val resp = http(req)
        allowOriginHeader(origin).map { ao =>
          val buff = List.newBuilder[Header]
          buff.addAll(ao)
          allowCredentialsHeader.foreach(buff.addOne)
          exposeHeadersHeader.foreach(buff.addOne)
          buff.result()
        } match {
          case Some(corsHeaders) => resp.map(_.putHeaders(corsHeaders: _*))
          case None => resp
        }
      }

      def allowOriginHeader(origin: Origin): Option[List[Header]] =
        allowOrigin match {
          case AllowOrigin.Any =>
            List(CommonHeaders.allowOriginWildcard).some
          case AllowOrigin.Match(p) =>
            if (p(origin))
              List(Header.Raw(`Access-Control-Allow-Origin`.name, origin.value)).some
            else
              None
        }

      if (allowOrigin == AllowOrigin.Any && allowCredentials == AllowCredentials.Allow) {
        logger.warn(
          "CORS disabled due to insecure config prohibited by spec. Call withCredentials(false) to avoid sharing credential-tainted responses with arbitrary origins, or call withAllowOrigin* method to be explicit who you trust with credential-tainted responses.")
        http
      } else
        Kleisli(dispatch)
    }

    def copy(
        allowOrigin: AllowOrigin = allowOrigin,
        allowCredentials: AllowCredentials = allowCredentials,
        exposeHeaders: ExposeHeaders = exposeHeaders
    ): Policy =
      new Policy(
        allowOrigin,
        allowCredentials,
        exposeHeaders
      )

    /** Allow CORS requests from any origin with an
      * `Access-Control-Allow-Origin` of `*`.
      */
    def withAllowAnyOrigin: Policy =
      copy(AllowOrigin.Any)

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
  }

  private val defaultPolicy: Policy = new Policy(
    AllowOrigin.Match(Function.const(false)),
    AllowCredentials.Deny,
    ExposeHeaders.None
  )

  /** A CORS policy that allows requests from any origin.
    *
    * @see {Policy#withAllowAnyOrigin}
    */
  val withAllowAnyOrigin: Policy =
    defaultPolicy.withAllowAnyOrigin

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
