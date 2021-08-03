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

import cats.Monad
import cats.data.NonEmptyList
import cats.mtl._
import cats.syntax.all._
import org.http4s.Method.OPTIONS
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
  def apply[F[_], G[_]](http: F[Response[G]], config: CORSConfig = CORSConfig.default)(implicit
      F: Monad[F],
      A: Ask[F, Request[G]]): F[Response[G]] =
    A.ask.flatMap { req =>
      // In the case of an options request we want to return a simple response with the correct Headers set.
      def createOptionsResponse(origin: Header.Raw, acrm: Header.Raw): Response[G] =
        corsHeaders(origin.value, acrm.value, isPreflight = true)(Response())

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

      def corsHeaders(origin: String, acrm: String, isPreflight: Boolean)(
          resp: Response[G]): Response[G] = {
        val withMethodBasedHeader = methodBasedHeader(isPreflight)
          .fold(resp)(h => resp.putHeaders(h))

        varyHeader(withMethodBasedHeader)
          .putHeaders(
            // TODO true is the only value here
            "Access-Control-Allow-Credentials" -> config.allowCredentials.toString,
            // TODO model me
            "Access-Control-Allow-Methods" -> config.allowedMethods.fold(acrm)(
              _.mkString("", ", ", "")),
            // TODO model me
            "Access-Control-Allow-Origin" -> origin,
            // TODO model me
            "Access-Control-Max-Age" -> config.maxAge.toSeconds.toString
          )
      }

      def allowCORS(origin: Header.Raw, acrm: Header.Raw): Boolean =
        (config.anyOrigin, config.anyMethod, origin.value, acrm.value) match {
          case (true, true, _, _) => true
          case (true, false, _, acrm) =>
            config.allowedMethods.exists(_.find(_.name === acrm).nonEmpty)
          case (false, true, origin, _) => config.allowedOrigins(origin)
          case (false, false, origin, acrm) =>
            config.allowedMethods.exists(_.find(_.name === acrm).nonEmpty) &&
              config.allowedOrigins(origin)
        }

      def headerFromStrings(headerName: String, values: Set[String]): Header.Raw =
        Header.Raw(CIString(headerName), values.mkString("", ", ", ""))

      (
        req.method,
        req.headers.get(ci"Origin"),
        req.headers.get(ci"Access-Control-Request-Method")) match {
        case (OPTIONS, Some(NonEmptyList(origin, _)), Some(NonEmptyList(acrm, _)))
            if allowCORS(origin, acrm) =>
          logger.debug(s"Serving OPTIONS with CORS headers for $acrm ${req.uri}")
          createOptionsResponse(origin, acrm).pure[F]
        case (_, Some(NonEmptyList(origin, _)), _) =>
          if (allowCORS(
              origin,
              Header.Raw(ci"Access-Control-Request-Method", req.method.renderString)))
            http.map { resp =>
              logger.debug(s"Adding CORS headers to ${req.method} ${req.uri}")
              corsHeaders(origin.value, req.method.renderString, isPreflight = false)(resp)
            }
          else {
            logger.debug(s"CORS headers were denied for ${req.method} ${req.uri}")
            Response(status = Status.Forbidden).pure[F]
          }
        case _ =>
          // This request is out of scope for CORS
          http
      }
    }

  def httpRoutes[F[_]](httpRoutes: HttpRoutes[F])(implicit F: Monad[F]): HttpRoutes[F] =
    apply(httpRoutes)

  def httpApp[F[_]](httpApp: HttpApp[F])(implicit F: Monad[F]): HttpApp[F] =
    apply(httpApp)
}
