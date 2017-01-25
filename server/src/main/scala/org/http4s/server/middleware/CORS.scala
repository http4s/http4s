package org.http4s
package server
package middleware

import Method.OPTIONS

import scala.concurrent.duration._

import fs2._
import org.http4s.batteries._
import org.http4s.headers._
import org.log4s.getLogger

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
  allowedOrigins: Option[Set[String]] = None,
  allowedMethods: Option[Set[String]] = None,
  allowedHeaders: Option[Set[String]] = Set("Content-Type", "*").some
)

object CORS {
  private[CORS] val logger = getLogger

  private[CORS] val ok =
    Service.constVal[Request, MaybeResponse](Response(Status.Ok))

  def DefaultCORSConfig = CORSConfig(
    anyOrigin = true,
    allowCredentials = true,
    maxAge = 1.day.toSeconds)

  /**
   * CORS middleware
   * This middleware provides clients with CORS information
   * based on information in CORS config.
   * Currently, you cannot make permissions depend on request details
   */
  def apply(service: HttpService, config: CORSConfig = DefaultCORSConfig): HttpService = Service.lift { req =>

    def options(origin: Header, acrm: Header): HttpService =
      (service |+| ok).map {
        case resp: Response =>
          if (resp.status.isSuccess)
            corsHeaders(origin.value, acrm.value)(resp)
          else {
            logger.info(s"CORS headers would have been allowed for ${req.method} ${req.uri}")
            resp
          }
        case Pass =>
          logger.warn("Unexpected Pass in CORS. This is probably a bug.")
          Pass
      }

    def corsHeaders(origin: String, acrm: String)(resp: Response): Response =
      config.allowedHeaders.map(_.mkString("", ", ", "")).fold(resp) { hs =>
        resp.putHeaders(Header("Access-Control-Allow-Headers", hs))
      }.putHeaders(
          Header("Vary", "Origin,Access-Control-Request-Methods"),
          Header("Access-Control-Allow-Credentials", config.allowCredentials.toString()),
          Header("Access-Control-Allow-Methods", config.allowedMethods.fold(acrm)(_.mkString("", ", ", ""))),
          Header("Access-Control-Allow-Origin", origin),
          Header("Access-Control-Max-Age", config.maxAge.toString())
        )

    def allowCORS(origin: Header, acrm: Header): Boolean = (config.anyOrigin, config.anyMethod, origin.value, acrm.value) match {
      case (true, true, _, _) => true
      case (true, false, _, acrm) => config.allowedMethods.map(_.contains(acrm)).getOrElse(false)
      case (false, true, origin, _) => config.allowedOrigins.map(_.contains(origin)).getOrElse(false)
      case (false, false, origin, acrm) =>
        (config.allowedMethods.map(_.contains(acrm)) |@|
          config.allowedOrigins.map(_.contains(origin))).map {
          _ && _
        }.getOrElse(false)
    }

    (req.method, req.headers.get(Origin), req.headers.get(`Access-Control-Request-Method`)) match {
      case (OPTIONS, Some(origin), Some(acrm)) if allowCORS(origin, acrm) =>
        logger.debug(s"Serving OPTIONS with CORS headers for ${acrm} ${req.uri}")
        options(origin, acrm)(req)
      case (_, Some(origin), _) if allowCORS(origin, Header("Access-Control-Request-Method", req.method.renderString)) =>
        logger.debug(s"Adding CORS headers to ${req.method} ${req.uri}")
        service(req).map(_.cata(corsHeaders(origin.value, req.method.renderString), Pass))
      case _ =>
        logger.info(s"CORS headers were denied for ${req.method} ${req.uri}")
        service(req)
    }
  }
}
