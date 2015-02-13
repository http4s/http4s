package org.http4s
package server
package middleware

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

case class CORSConfig(
  anyOrigin: Boolean,
  allowCredentials: Boolean,
  maxAge: Long,
  anyMethod: Boolean = true,
  allowedOrigins: Option[Set[String]] = None,
  allowedMethods: Option[Set[String]] = None,
  allowedHeaders: Option[Set[String]] = None
)

class CORS(service: HttpService, config: CORSConfig) extends CORS.CORSF {
  import CORS._

  def apply(req: Request): Task[Option[Response]] = ((req.method, req.headers.get(Origin), req.headers.get(`Access-Control-Request-Method`)) match {
    case (Method.OPTIONS, Some(origin), Some(acrm)) if allowCORS(origin, acrm) =>
      options(req, origin, acrm)
    case (_, Some(origin), Some(acrm)) if allowCORS(origin, acrm) =>
      service.runT(req).map(corsHeaders(_, origin.value, acrm.value, acrh(req))).run
    case _ => service(req)
  })

  def options(req: Request, origin: Header, acrm: Header): Task[Option[Response]] = {
    logger.debug(s"Options request for $origin")
    service(req).map{_.cata(
      corsHeaders(_, origin.value, acrm.value, acrh(req)).some,
      corsHeaders(Response(), origin.value, acrm.value, acrh(req)).some
    )}
  }

  def corsHeaders(resp: Response, origin: String, acrm: String, acrh: String): Response = {
    logger.debug(s"Adding CORS headers to $resp")
    resp.putHeaders(
      Header("Vary", "Origin,Access-Control-Request-Methods"),
      Header("Access-Control-Max-Age", config.maxAge.toString()),
      Header("Access-Control-Allow-Credentials", config.allowCredentials.toString()),
      Header("Access-Control-Allow-Origin", origin),
      Header("Access-Control-Allow-Methods", config.allowedMethods.cata(
        am =>  am.mkString("", " ", ""),
        acrm)),
      Header("Access-Control-Allow-Headers", config.allowedHeaders.cata(
        ah => ah.mkString("", " ", ""),
        acrh))
    )
  }

  def allowCORS(origin: Header, acrm: Header) : Boolean = (config.anyOrigin, config.anyMethod, origin.value, acrm.value) match {
    case (true, true, _, _)           => true
    case (true, false, _, acrm)       => config.allowedMethods.map(_.contains(acrm))   | false
    case (false, true, origin, _)     => config.allowedOrigins.map(_.contains(origin)) | false
    case (false, false, origin, acrm) =>
      (config.allowedMethods.map(_.contains(acrm)) |@|
       config.allowedOrigins.map(_.contains(origin))) {_ && _} | false
  }

  def acrh(req: Request) =
    req.headers.get(`Access-Control-Request-Headers`).map(_.value) | ""

}

object CORS {
  type CORSF = Function1[Request, Task[Option[Response]]]
  private[CORS] val logger = getLogger

  def DefaultCORSConfig = CORSConfig(
    anyOrigin= true,
    allowCredentials= true,
    maxAge = 1.day.toSeconds)

  def apply(service: HttpService, config: CORSConfig = DefaultCORSConfig): HttpService = Service.lift(new CORS(service, config))
}
