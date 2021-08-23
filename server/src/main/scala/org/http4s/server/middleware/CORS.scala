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
import cats.data.Kleisli
import cats.syntax.all._
import org.http4s.Method.OPTIONS
import org.http4s.headers._
import org.http4s.util.CaseInsensitiveString
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
  }

  sealed trait AllowOrigin
  object AllowOrigin {
    case object Any extends AllowOrigin
  }

  final class Policy private[CORS] (allowOrigin: AllowOrigin) {
    def apply[F[_]: Functor, G[_]](http: Http[F, G]): Http[F, G] = Kleisli { req =>
      def dispatch =
        req.headers.get(Origin) match {
          case Some(origin) =>
            handleCors(origin)
          case None =>
            http(req)
        }

      def handleCors(origin: Origin) = {
        val resp = http(req)
        allowOriginsHeader(origin) match {
          case Some(corsHeader) => resp.map(_.putHeaders(corsHeader))
          case None => resp
        }
      }

      def allowOriginsHeader(origin: Origin): Option[Header] = {
        val _ = origin
        allowOrigin match {
          case AllowOrigin.Any =>
            CommonHeaders.someAllowOriginWildcard
        }
      }

      dispatch
    }

    def copy(allowOrigin: AllowOrigin = allowOrigin): Policy =
      new Policy(
        allowOrigin
      )

    /** Allow requests from any origin with an
      * `Access-Control-Allow-Origin` of `*`.
      */
    def withAllowAnyOrigin: Policy =
      copy(AllowOrigin.Any)
  }

  /** A CORS policy that allows requests from any origin with an
    * `Access-Control-Allow-Origin` of `*`.
    */
  val withAllowAnyOrigin: Policy =
    new Policy(AllowOrigin.Any)

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
        response.headers.get(CaseInsensitiveString("Vary")) match {
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
