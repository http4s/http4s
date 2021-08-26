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

import cats.{Applicative, Monad}
import cats.data.{Kleisli}
import cats.syntax.all._
import org.http4s.Method.OPTIONS
import org.http4s.headers._
import org.http4s.syntax.header._
import org.log4s.getLogger
import org.typelevel.ci._
import scala.concurrent.duration._
import scala.util.hashing.MurmurHash3

/** CORS middleware config options.
  * You can give an instance of this class to the CORS middleware,
  * to specify its behavior
  */

final class CORSConfig private (
    val anyOrigin: Boolean,
    val allowCredentials: Boolean,
    val maxAge: FiniteDuration,
    val anyMethod: Boolean,
    val allowedOrigins: String => Boolean,
    val allowedMethods: Option[Set[Method]],
    val allowedHeaders: Option[Set[String]],
    val exposedHeaders: Option[Set[String]]
) {

  private def copy(
      anyOrigin: Boolean = anyOrigin,
      allowCredentials: Boolean = allowCredentials,
      maxAge: FiniteDuration = maxAge,
      anyMethod: Boolean = anyMethod,
      allowedOrigins: String => Boolean = allowedOrigins,
      allowedMethods: Option[Set[Method]] = allowedMethods,
      allowedHeaders: Option[Set[String]] = allowedHeaders,
      exposedHeaders: Option[Set[String]] = exposedHeaders
  ) = new CORSConfig(
    anyOrigin,
    allowCredentials,
    maxAge,
    anyMethod,
    allowedOrigins,
    allowedMethods,
    allowedHeaders,
    exposedHeaders
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
    exposedHeaders = Set("*").some
  )
}

object CORS {
  private[CORS] val logger = getLogger

  val defaultVaryHeader = Header.Raw(ci"Vary", "Origin,Access-Control-Request-Method")

  /** CORS middleware
    * This middleware provides clients with CORS information
    * based on information in CORS config.
    * Currently, you cannot make permissions depend on request details
    */
  def apply[F[_], G[_]](http: Http[F, G], config: CORSConfig = CORSConfig.default)(implicit
      F: Applicative[F]): Http[F, G] =
    Kleisli { req =>
      // In the case of an options request we want to return a simple response with the correct Headers set.
      def createOptionsResponse(
          origin: Origin,
          acrm: `Access-Control-Request-Method`): Response[G] =
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

      def corsHeaders(origin: Origin, method: Method, isPreflight: Boolean)(
          resp: Response[G]): Response[G] = {
        val withMethodBasedHeader = methodBasedHeader(isPreflight)
          .fold(resp)(h => resp.putHeaders(h))

        varyHeader(withMethodBasedHeader)
          .putHeaders(
            // TODO true is the only value here
            "Access-Control-Allow-Credentials" -> config.allowCredentials.toString,
            // TODO model me
            "Access-Control-Allow-Methods" -> config.allowedMethods.fold(method.renderString)(
              _.mkString("", ", ", "")),
            // TODO model me
            "Access-Control-Allow-Origin" -> origin.value,
            // TODO model me
            "Access-Control-Max-Age" -> config.maxAge.toSeconds.toString
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
        req.headers.get[`Access-Control-Request-Method`]) match {
        case (OPTIONS, Some(origin), Some(acrm)) if allowCORS(origin, acrm.method) =>
          logger.debug(s"Serving OPTIONS with CORS headers for $acrm ${req.uri}")
          createOptionsResponse(origin, acrm).pure[F]
        case (_, Some(origin), _) =>
          if (allowCORS(origin, req.method))
            http(req).map { resp =>
              logger.debug(s"Adding CORS headers to ${req.method} ${req.uri}")
              corsHeaders(origin, req.method, isPreflight = false)(resp)
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

  def httpRoutes[F[_]: Monad](httpRoutes: HttpRoutes[F]): HttpRoutes[F] =
    httpRoutes(httpRoutes,CORSConfig)

  def httpApp[F[_]: Applicative](httpApp: HttpApp[F]): HttpApp[F] =
    httpApp(httpApp,CORSConfig)
 
  def httpRoutes[F[_]: Monad](httpRoutes: HttpRoutes[F], config: CORSConfig): HttpRoutes[F] = 
    apply(httpRoutes,config)

  def httpApp[F[_]: Applicative](httpApp: HttpApp[F], config: CORSConfig): HttpApp[F] = 
    apply(httpApp,CORSConfig)
 
 
}

