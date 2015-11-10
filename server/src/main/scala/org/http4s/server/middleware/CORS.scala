package org.http4s
package server
package middleware

import Method.OPTIONS

import org.http4s.headers.{
  `Access-Control-Allow-Origin`,
  `Access-Control-Allow-Credentials`,
  `Access-Control-Allow-Methods`,
  `Access-Control-Allow-Headers`,
  `Access-Control-Expose-Headers`,
  `Access-Control-Max-Age`,
  `Access-Control-Request-Method`,
  `Access-Control-Request-Headers`,
  Origin
}

import org.log4s.getLogger

import scala.concurrent.duration._

import scalaz._
import Scalaz._
import scalaz.concurrent.Task
import scalaz.Kleisli._

/**
  * CORS middleware config options.
  * You can give an instance of this class to the CORS middleware,
  * to specify its behavoir
  */
case class CORSConfig(
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
    Service.constVal[Request, Response](Response(Status.Ok))

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
      Service.withFallback(ok)(service).map { resp =>
        if (resp.status.isSuccess)
          corsHeaders(origin.value, acrm.value)(resp)
        else {
          logger.info(s"CORS headers would have been allowed for ${req.method} ${req.uri}")
          resp
        }
      }

    def corsHeaders(origin: String, acrm: String)(resp: Response): Response =
      config.allowedHeaders.map(_.mkString("", ", ", "")).cata(
        (hs: String) => resp.putHeaders(Header("Access-Control-Allow-Headers", hs)),
        resp
      ).putHeaders(
          Header("Vary", "Origin,Access-Control-Request-Methods"),
          Header("Access-Control-Allow-Credentials", config.allowCredentials.toString()),
          Header("Access-Control-Allow-Methods", config.allowedMethods.cata(_.mkString("", ", ", ""), acrm)),
          Header("Access-Control-Allow-Origin", origin),
          Header("Access-Control-Max-Age", config.maxAge.toString())
        )

    def allowCORS(origin: Header, acrm: Header): Boolean = (config.anyOrigin, config.anyMethod, origin.value, acrm.value) match {
      case (true, true, _, _) => true
      case (true, false, _, acrm) => config.allowedMethods.map(_.contains(acrm)) | false
      case (false, true, origin, _) => config.allowedOrigins.map(_.contains(origin)) | false
      case (false, false, origin, acrm) =>
        (config.allowedMethods.map(_.contains(acrm)) |@|
          config.allowedOrigins.map(_.contains(origin))) {
          _ && _
        } | false
    }

    (req.method, req.headers.get(Origin), req.headers.get(`Access-Control-Request-Method`)) match {
      case (OPTIONS, Some(origin), Some(acrm)) if allowCORS(origin, acrm) =>
        logger.debug(s"Serving OPTIONS with CORS headers for ${acrm} ${req.uri}")
        options(origin, acrm)(req)
      case (_, Some(origin), _) if allowCORS(origin, Header("Access-Control-Request-Method", req.method.renderString)) =>
        logger.debug(s"Adding CORS headers to ${req.method} ${req.uri}")
        service(req).map(corsHeaders(origin.value, req.method.renderString))
      case _ =>
        logger.info(s"CORS headers were denied for ${req.method} ${req.uri}")
        service(req)
    }
  }
}
