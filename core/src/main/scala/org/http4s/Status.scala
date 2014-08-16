package org.http4s

import java.util.concurrent.atomic.AtomicReferenceArray
import scalaz.{Show, Equal}

/** Representation of the HTTP response code and reason
  *
  * @param code HTTP status code
  * @param reason reason for the response. eg, OK
  */
sealed abstract case class Status (code: Int, reason: String)(val isEntityAllowed: Boolean) extends Ordered[Status] {
  def compare(that: Status) = code.compareTo(that.code)

  def line = {
    val buf = new StringBuilder(reason.length + 5)
    buf.append(code)
    buf.append(' ')
    buf.append(reason)
    buf.toString()
  }
}

object Status {
  def get(code: Int): Option[Status] = Option(registry.get(code))

  def apply(code: Int, reason: String, isEntityAllowed: Boolean): Status =
    new Status(code, reason)(isEntityAllowed) {}

  private val registry = new AtomicReferenceArray[Status](600)

  def register(status: Status): status.type = {
    registry.set(status.code, status)
    status
  }

  /**
   * Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
   */
  val Continue = register(Status(100, "Continue", false))
  val SwitchingProtocols = register(Status(101, "Switching Protocols", false))
  val Processing = register(Status(102, "Processing", false))

  val Ok = register(Status(200, "OK", true))
  val Created = register(Status(201, "Created", true))
  val Accepted = register(Status(202, "Accepted", true))
  val NonAuthoritativeInformation = register(Status(203, "Non-Authoritative Information", true))
  val NoContent = register(Status(204, "No Content", false))
  val ResetContent = register(Status(205, "Reset Content", false))
  val PartialContent = register(Status(206, "Partial Content", true))
  val MultiStatus = register(Status(207, "Multi-Status", true))
  val AlreadyReported = register(Status(208, "Already Reported", true))
  val IMUsed = register(Status(226, "IM Used", true))

  val MultipleChoices = register(Status(300, "Multiple Choices", true))
  val MovedPermanently = register(Status(301, "Moved Permanently", true))
  val Found = register(Status(302, "Found", true))
  val SeeOther = register(Status(303, "See Other", true))
  val NotModified = register(Status(304, "Not Modified", false))
  val UseProxy = register(Status(305, "Use Proxy", true))
  val TemporaryRedirect = register(Status(307, "Temporary Redirect", true))
  val PermanentRedirect = register(Status(308, "Permanent Redirect", true))

  val BadRequest = register(Status(400, "Bad Request", true))
  val Unauthorized = register(Status(401, "Unauthorized", true))
  val PaymentRequired = register(Status(402, "Payment Required", true))
  val Forbidden = register(Status(403, "Forbidden", true))
  val NotFound = register(Status(404, "Not Found", true))
  val MethodNotAllowed = register(Status(405, "Method Not Allowed", true))
  val NotAcceptable = register(Status(406, "Not Acceptable", true))
  val ProxyAuthenticationRequired = register(Status(407, "Proxy Authentication Required", true))
  val RequestTimeout = register(Status(408, "Request Timeout", true))
  val Conflict = register(Status(409, "Conflict", true))
  val Gone = register(Status(410, "Gone", true))
  val LengthRequired = register(Status(411, "Length Required", true))
  val PreconditionFailed = register(Status(412, "Precondition Failed", true))
  val PayloadTooLarge = register(Status(413, "Payload Too Large", true))
  val UriTooLong = register(Status(414, "URI Too Long", true))
  val UnsupportedMediaType = register(Status(415, "Unsupported Media Type", true))
  val RangeNotSatisfiable = register(Status(416, "Range Not Satisfiable", true))
  val ExpectationFailed = register(Status(417, "Expectation Failed", true))
  val UnprocessableEntity = register(Status(422, "Unprocessable Entity", true))
  val Locked = register(Status(423, "Locked", true))
  val FailedDependency = register(Status(424, "Failed Dependency", true))
  val UpgradeRequired = register(Status(426, "Upgrade Required", true))
  val PreconditionRequired = register(Status(428, "Precondition Required", true))
  val TooManyRequests = register(Status(429, "Too Many Requests", true))
  val RequestHeaderFieldsTooLarge = register(Status(431, "Request Header Fields Too Large", true))

  val InternalServerError = register(Status(500, "Internal Server Error", true))
  val NotImplemented = register(Status(501, "Not Implemented", true))
  val BadGateway = register(Status(502, "Bad Gateway", true))
  val ServiceUnavailable = register(Status(503, "Service Unavailable", true))
  val GatewayTimeout = register(Status(504, "Gateway Timeout", true))
  val HttpVersionNotSupported = register(Status(505, "HTTP Version not supported", true))
  val VariantAlsoNegotiates = register(Status(506, "Variant Also Negotiates", true))
  val InsufficientStorage = register(Status(507, "Insufficient Storage", true))
  val LoopDetected = register(Status(508, "Loop Detected", true))
  val NotExtended = register(Status(510, "Not Extended", true))
  val NetworkAuthenticationRequired = register(Status(511, "Network Authentication Required", true))
}

trait StatusInstances {
  implicit val StatusShow = Show.showFromToString[Status]
  implicit val StatusEqual = Equal.equalA[Status]
}
