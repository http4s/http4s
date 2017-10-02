package org.http4s
package server
package middleware

import cats._
import cats.data.{Kleisli, OptionT}
import cats.implicits._
import org.http4s.Method.OPTIONS
import org.http4s.headers._
import org.log4s.getLogger
import scala.concurrent.duration._

/**
  * CORS middleware config options.
  * You can give an instance of this class to the CORS middleware,
  * to specify its behavoir
  */
final case class CORSConfig(
    anyOrigin: Boolean,
    allowCredentials: Boolean,
    maxAge: Long,
    anyMethod: Boolean = true,
    allowedOrigins: String => Boolean = _ => false,
    allowedMethods: Option[Set[String]] = None,
    allowedHeaders: Option[Set[String]] = Set("Content-Type", "*").some,
    exposedHeaders: Option[Set[String]] = Set("*").some
)

object CORS {
  private[CORS] val logger = getLogger

  def DefaultCORSConfig =
    CORSConfig(anyOrigin = true, allowCredentials = true, maxAge = 1.day.toSeconds)

  /**
    * CORS middleware
    * This middleware provides clients with CORS information
    * based on information in CORS config.
    * Currently, you cannot make permissions depend on request details
    */
  def apply[F[_]](service: HttpService[F], config: CORSConfig = DefaultCORSConfig)(
      implicit F: Applicative[F]): HttpService[F] =
    Kleisli { req =>
      // In the case of an options request we want to return a simple response with the correct Headers set.
      def createOptionsResponse(origin: Header, acrm: Header): Response[F] =
        corsHeaders(origin.value, acrm.value, isPreflight = true)(Response())

      def corsHeaders(origin: String, acrm: String, isPreflight: Boolean)(
          resp: Response[F]): Response[F] = {
        val methodBasedHeader =
          if (isPreflight)
            config.allowedHeaders.map(headerFromStrings("Access-Control-Allow-Headers", _))
          else config.exposedHeaders.map(headerFromStrings("Access-Control-Expose-Headers", _))
        methodBasedHeader
          .fold(resp)(h => resp.putHeaders(h))
          .putHeaders(
            Header("Vary", "Origin,Access-Control-Request-Methods"),
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

      (req.method, req.headers.get(Origin), req.headers.get(`Access-Control-Request-Method`)) match {
        case (OPTIONS, Some(origin), Some(acrm)) if allowCORS(origin, acrm) =>
          logger.debug(s"Serving OPTIONS with CORS headers for $acrm ${req.uri}")
          OptionT.some(createOptionsResponse(origin, acrm))
        case (_, Some(origin), _) =>
          if (allowCORS(origin, Header("Access-Control-Request-Method", req.method.renderString))) {
            service(req).map { resp =>
              logger.debug(s"Adding CORS headers to ${req.method} ${req.uri}")
              corsHeaders(origin.value, req.method.renderString, isPreflight = false)(resp)
            }
          } else {
            logger.debug(s"CORS headers were denied for ${req.method} ${req.uri}")
            OptionT.some(Response(status = Status.Forbidden))
          }
        case _ =>
          // This request is out of scope for CORS
          service(req)
      }
    }
}
