package org.http4s

import scala.concurrent.Future

import play.api.libs.iteratee.{Input, Iteratee, Enumerator}

case class Responder[+A](
  statusLine: StatusLine = StatusLine.Ok,
  headers: Headers = Headers.Empty,
  body: A
) {
  def map[B](f: A => B): Responder[B] = copy(body = f(body))
}

case class StatusLine(code: Int, reason: String)

object StatusLine {
  val Continue = StatusLine(100, "Continue")
  val SwitchingProtocols = StatusLine(101, "Switching Protocols")
  val Ok = StatusLine(200, "OK")
  val Created = StatusLine(201, "Created")
  val Accepted = StatusLine(202, "Accepted")
  val NonAuthoritativeInformation = StatusLine(203, "Non-Authoritative Information")
  val NoContent = StatusLine(204, "No Content")
  val ResetContent = StatusLine(205, "Reset Content")
  val PartialContent = StatusLine(206, "Partial Content")
  val MultipleChoices = StatusLine(300, "Multiple Choices")
  val MovedPermanently = StatusLine(301, "Moved Permanently")
  val Found = StatusLine(302, "Found")
  val SeeOther = StatusLine(303, "See Other")
  val NotModified = StatusLine(304, "Not Modified")
  val UseProxy = StatusLine(305, "Use Proxy")
  val TemporaryRedirect = StatusLine(307, "Temporary Redirect")
  val BadRequest = StatusLine(400, "Bad Request")
  val Unauthorized = StatusLine(401, "Unauthorized")
  val PaymentRequired = StatusLine(402, "Payment Required")
  val Forbidden = StatusLine(403, "Forbidden")
  val NotFound = StatusLine(404, "Not Found")
  val MethodNotAllowed = StatusLine(405, "Method Not Allowed")
  val NotAcceptable = StatusLine(406, "Not Acceptable")
  val ProxyAuthenticationRequred = StatusLine(407, "Proxy Authentication Required")
  val RequestTimeOut = StatusLine(408, "Request Time-out")
  val Conflict = StatusLine(409, "Conflict")
  val Gone = StatusLine(410, "Gone")
  val LengthRequired = StatusLine(411, "Length Required")
  val PreconditionFailed = StatusLine(412, "Precondition Failed")
  val RequestEntityTooLarge = StatusLine(413, "Request Entity Too Large")
  val RequestUriTooLarge = StatusLine(414, "Request-URI Too Large")
  val UnsupportedMediaType = StatusLine(415, "Unsupported Media Type")
  val RequestedRangeNotSatisfiable = StatusLine(416, "Requested Range Not Satisfiable")
  val ExpectationFailed = StatusLine(417, "ExpectationFailed")
  val InternalServerError = StatusLine(500, "Internal Server Error")
  val NotImplemented = StatusLine(501, "Not Implemented")
  val BadGateway = StatusLine(502, "Bad Gateway")
  val ServiceUnavailable = StatusLine(503, "Service Unavailable")
  val GatewayTimeOut = StatusLine(504, "Gateway Time-out")
  val HttpVersionNotSupported = StatusLine(505, "HTTP Version not supported")
}
