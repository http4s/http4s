package org.http4s
package server
package middleware

import Method.OPTIONS

import scala.concurrent.duration._

import org.http4s.headers._
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

    // In the case of an options request we want to return a simple response with the correct Headers set.
    def createOptionsResponse(origin: Header, acrm: Header): Response = corsHeaders(origin.value, acrm.value, true)(Response())

    def corsHeaders(origin: FieldValue, acrm: FieldValue, isPreflight: Boolean)(resp: Response): Response = {
      val methodBasedHeader = if (isPreflight) {
        config.allowedHeaders.map(headerFromStrings(fn"Access-Control-Allow-Headers", _))
      }
      else {
        config.exposedHeaders.map(headerFromStrings(fn"Access-Control-Expose-Headers", _))
      }
      methodBasedHeader.fold(resp)(h => resp.putHeaders(h)).putHeaders(
        Header(fn"Vary", fv"Origin,Access-Control-Request-Methods"),
        Header(fn"Access-Control-Allow-Credentials", FieldValue.unsafeFromString(config.allowCredentials.toString())),
        Header(fn"Access-Control-Allow-Methods", config.allowedMethods.fold(acrm)(m => FieldValue.unsafeFromString(m.mkString("", ", ", "")))),
        Header(fn"Access-Control-Allow-Origin", origin),
        Header(fn"Access-Control-Max-Age", FieldValue.unsafeFromString(config.maxAge.toString()))
      )
    }

    def headerFromStrings(headerName: FieldName, values: Set[String]): Header = Header(headerName, FieldValue.unsafeFromString(values.mkString("", ", ", "")))

    def allowCORS(origin: Header, acrm: Header): Boolean = (config.anyOrigin, config.anyMethod, origin.value, acrm.value) match {
      case (true, true, _, _) => true
      case (true, false, _, acrm) => config.allowedMethods.map(_.contains(acrm.toString)).getOrElse(false)
      case (false, true, origin, _) => config.allowedOrigins(origin.toString)
      case (false, false, origin, acrm) =>
        (config.allowedMethods.map(_.contains(acrm.toString)).getOrElse(false) && config.allowedOrigins(origin.toString))
    }

    (req.method, req.headers.get(Origin), req.headers.get(`Access-Control-Request-Method`)) match {
      case (OPTIONS, Some(origin), Some(acrm)) if allowCORS(origin, acrm) =>
        logger.debug(s"Serving OPTIONS with CORS headers for ${acrm} ${req.uri}")
        Task.now(createOptionsResponse(origin, acrm))
      case (_, Some(origin), _) =>
        if (allowCORS(origin, Header(fn"Access-Control-Request-Method", FieldValue.unsafeFromString(req.method.renderString)))) {
          service(req).map {
            case resp: Response =>
              logger.debug(s"Adding CORS headers to ${req.method} ${req.uri}")
              corsHeaders(origin.value, FieldValue.unsafeFromString(req.method.renderString), false)(resp)
            case Pass =>
              Pass
          }
        }
        else {
          logger.debug(s"CORS headers were denied for ${req.method} ${req.uri}")
          Task.now(Response(status = Status.Forbidden))
        }
      case _ =>
        // This request is out of scope for CORS
        service(req)
    }
  }
}
