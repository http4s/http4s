package org.http4s



import scala.concurrent.Future

import play.api.libs.iteratee.{Enumeratee, Input, Iteratee, Enumerator}

case class Responder[A](
  statusLine: StatusLine = StatusLine.Ok,
  headers: Headers = Headers.Empty,
  body: Enumerator[A] = Enumerator.eof
) {
  import scala.language.reflectiveCalls // So the compiler doesn't complain...
  def map[B](f: A => B): Responder[B] = copy(body = body &> Enumeratee.map(f)) : Responder[B]
}

case class StatusLine(code: Int, reason: String) extends Ordered[StatusLine] {
  def compare(that: StatusLine) = code.compareTo(that.code)

  def line = {
    val buf = new StringBuilder(reason.length + 5)
    buf.append(code)
    buf.append(' ')
    buf.append(reason)
    buf.toString()
  }
}

object StatusLine {
  /**
   * Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
   */
  val Continue = StatusLine(100, "Continue")
  val SwitchingProtocols = StatusLine(101, "Switching Protocols")
  val Processing = StatusLine(102, "Processing")
  val Ok = StatusLine(200, "OK")
  val Created = StatusLine(201, "Created")
  val Accepted = StatusLine(202, "Accepted")
  val NonAuthoritativeInformation = StatusLine(203, "Non-Authoritative Information")
  val NoContent = StatusLine(204, "No Content")
  val ResetContent = StatusLine(205, "Reset Content")
  val PartialContent = StatusLine(206, "Partial Content")
  val MultiStatus = StatusLine(207, "Multi-Status")
  val AlreadyReported = StatusLine(208, "Already Reported")
  val IMUsed = StatusLine(226, "IM Used")
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
  val ImATeapot = StatusLine(418, "I'm a teapot")
  val UnprocessableEntity = StatusLine(422, "Unprocessable Entity")
  val Locked = StatusLine(423, "Locked")
  val FailedDependency = StatusLine(424, "Failed Dependency")
  val UnorderedCollection = StatusLine(425, "Unordered Collection")
  val UpgradeRequired = StatusLine(426, "Upgrade Required")
  val PreconditionRequired = StatusLine(428, "Precondition Required")
  val TooManyRequests = StatusLine(429, "Too Many Requests")
  val RequestHeaderFieldsTooLarge = StatusLine(431, "Request Header Fields Too Large")
  val InternalServerError = StatusLine(500, "Internal Server Error")
  val NotImplemented = StatusLine(501, "Not Implemented")
  val BadGateway = StatusLine(502, "Bad Gateway")
  val ServiceUnavailable = StatusLine(503, "Service Unavailable")
  val GatewayTimeOut = StatusLine(504, "Gateway Time-out")
  val HttpVersionNotSupported = StatusLine(505, "HTTP Version not supported")
  val VariantAlsoNegotiates = StatusLine(506, "Variant Also Negotiates")
  val InsufficientStorage = StatusLine(507, "Insufficient Storage")
  val LoopDetected = StatusLine(508, "Loop Detected")
  val NotExtended = StatusLine(510, "Not Extended")
  val NetworkAuthenticationRequired = StatusLine(511, "Network Authentication Required")

  /**
   * Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
   */
  private[this] val ReasonMap = Map(
    100 -> "Continue",
    101 -> "Switching Protocols",
    102 -> "Processing",
    200 -> "OK",
    201 -> "Created",
    202 -> "Accepted",
    203 -> "Non-Authoritative Information",
    204 -> "No Content",
    205 -> "Reset Content",
    206 -> "Partial Content",
    207 -> "Multi-Status",
    208 -> "Already Reported",
    226 -> "IM Used",
    300 -> "Multiple Choices",
    301 -> "Moved Permanently",
    302 -> "Found",
    303 -> "See Other",
    304 -> "Not Modified",
    305 -> "Use Proxy",
    307 -> "Temporary Redirect",
    400 -> "Bad Request",
    401 -> "Unauthorized",
    402 -> "Payment Required",
    403 -> "Forbidden",
    404 -> "Not Found",
    405 -> "Method Not Allowed",
    406 -> "Not Acceptable",
    407 -> "Proxy Authentication Required",
    408 -> "Request Timeout",
    409 -> "Conflict",
    410 -> "Gone",
    411 -> "Length Required",
    412 -> "Precondition Failed",
    413 -> "Request Entity Too Large",
    414 -> "Request-URI Too Long",
    415 -> "Unsupported Media Type",
    416 -> "Requested Range Not Satisfiable",
    417 -> "Expectation Failed",
    418 -> "I'm a teapot",
    422 -> "Unprocessable Entity",
    423 -> "Locked",
    424 -> "Failed Dependency",
    425 -> "Unordered Collection",
    426 -> "Upgrade Required",
    428 -> "Precondition Required",
    429 -> "Too Many Requests",
    431 -> "Request Header Fields Too Large",
    500 -> "Internal Server Error",
    501 -> "Not Implemented",
    502 -> "Bad Gateway",
    503 -> "Service Unavailable",
    504 -> "Gateway Timeout",
    505 -> "HTTP Version Not Supported",
    506 -> "Variant Also Negotiates",
    507 -> "Insufficient Storage",
    508 -> "Loop Detected",
    510 -> "Not Extended",
    511 -> "Network Authentication Required"
  )


  def apply(code: Int): StatusLine =
    StatusLine(code, ReasonMap.getOrElse(code, ""))

}
