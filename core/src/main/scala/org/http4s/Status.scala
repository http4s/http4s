package org.http4s

import org.http4s.Status.ResponseConstraints

/** Representation of the HTTP response code and reason
  *
  * @param code HTTP status code
  * @param reason reason for the response. eg, OK
  */
sealed abstract case class Status(code: Int, reason: String) extends Ordered[Status] {
  self: ResponseConstraints[_] =>

  def compare(that: Status) = code.compareTo(that.code)

  def line = {
    val buf = new StringBuilder(reason.length + 5)
    buf.append(code)
    buf.append(' ')
    buf.append(reason)
    buf.toString()
  }
}

object Status extends StatusConstants {
  sealed trait ResponseConstraints[A <: ResponseConstraints[A]]
  trait NoConstraints extends ResponseConstraints[NoConstraints]
  trait EntityProhibited extends ResponseConstraints[EntityProhibited]
  trait HeaderRequired[H] extends ResponseConstraints[HeaderRequired[H]] {
    def mkRequiredHeader: H => Header
  }
  trait LocationRequired extends HeaderRequired[Uri] {
    def mkRequiredHeader = Header.Location(_)
  }

  private[this] val ReasonMap = Map(
    (for {
      line <- getClass.getMethods
      if line.getReturnType.isAssignableFrom(classOf[Status]) && line.getParameterTypes.isEmpty
      status = line.invoke(this).asInstanceOf[Status]
    } yield status.code -> status.reason):_*
  )

  def apply(code: Int): Status = new Status(code, ReasonMap.getOrElse(code, "")) with NoConstraints
  def apply(code: Int, reason: String): Status = new Status(code, reason) with NoConstraints
}

trait StatusConstants {
  import Status.{NoConstraints, EntityProhibited, HeaderRequired, LocationRequired}

  /**
   * Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
   */
  val Continue = new Status(100, "Continue") with NoConstraints
  val SwitchingProtocols = new Status(101, "Switching Protocols") with HeaderRequired[String] {
    def mkRequiredHeader = Header.Raw(Header.Upgrade.name, _)
  }
  val Processing = new Status(102, "Processing") with NoConstraints

  val Ok = new Status(200, "OK") with NoConstraints
  val Created = new Status(201, "Created") with NoConstraints
  val Accepted = new Status(202, "Accepted") with NoConstraints
  val NonAuthoritativeInformation = new Status(203, "Non-Authoritative Information") with NoConstraints
  val NoContent = new Status(204, "No Content") with EntityProhibited
  val ResetContent = new Status(205, "Reset Content") with EntityProhibited
  val PartialContent = new Status(206, "Partial Content") with NoConstraints
  val MultiStatus = new Status(207, "Multi-Status") with NoConstraints
  val AlreadyReported = new Status(208, "Already Reported") with NoConstraints
  val IMUsed = new Status(226, "IM Used") with NoConstraints

  val MultipleChoices = new Status(300, "Multiple Choices") with NoConstraints
  val MovedPermanently = new Status(301, "Moved Permanently") with LocationRequired
  val Found = new Status(302, "Found") with LocationRequired
  val SeeOther = new Status(303, "See Other") with LocationRequired
  val NotModified = new Status(304, "Not Modified") with EntityProhibited
  val UseProxy = new Status(305, "Use Proxy") with LocationRequired
  val TemporaryRedirect = new Status(306, "Temporary Redirect") with LocationRequired

  val BadRequest = new Status(400, "Bad Request") with NoConstraints
  val Unauthorized = new Status(401, "Unauthorized") with HeaderRequired[Challenge] {
    def mkRequiredHeader = Header.`WWW-Authenticate`(_)
  }
  val PaymentRequired = new Status(402, "Payment Required") with NoConstraints
  val Forbidden = new Status(403, "Forbidden") with NoConstraints
  val NotFound = new Status(404, "Not Found") with NoConstraints
  val MethodNotAllowed = new Status(405, "Method Not Allowed") with HeaderRequired[String] {
    def mkRequiredHeader = Header.Raw(Header.Allow.name, _)
  }
  val NotAcceptable = new Status(406, "Not Acceptable") with NoConstraints
  val ProxyAuthenticationRequired = new Status(407, "Proxy Authentication Required") with HeaderRequired[String] {
    def mkRequiredHeader = Header.Raw(Header.`Proxy-Authenticate`.name, _)
  }
  val RequestTimeout = new Status(408, "Request Timeout") with NoConstraints
  val Conflict = new Status(409, "Conflict") with NoConstraints
  val Gone = new Status(410, "Gone") with NoConstraints
  val LengthRequired = new Status(411, "Length Required") with NoConstraints
  val PreconditionFailed = new Status(412, "Precondition Failed") with NoConstraints
  val RequestEntityTooLarge = new Status(413, "Request Entity Too Large") with NoConstraints
  val RequestUriTooLarge = new Status(414, "Request-URI Too Large") with NoConstraints
  val UnsupportedMediaType = new Status(415, "Unsupported Media Type") with NoConstraints
  val RequestedRangeNotSatisfiable = new Status(416, "Requested Range Not Satisfiable") with NoConstraints
  val ExpectationFailed = new Status(417, "ExpectationFailed") with NoConstraints
  val ImATeapot = new Status(418, "I'm a teapot") with NoConstraints
  val UnprocessableEntity = new Status(422, "Unprocessable Entity") with NoConstraints
  val Locked = new Status(423, "Locked") with NoConstraints
  val FailedDependency = new Status(424, "Failed Dependency") with NoConstraints
  val UnorderedCollection = new Status(425, "Unordered Collection") with NoConstraints
  val UpgradeRequired = new Status(426, "Upgrade Required") with NoConstraints
  val PreconditionRequired = new Status(428, "Precondition Required") with NoConstraints
  val TooManyRequests = new Status(429, "Too Many Requests") with NoConstraints
  val RequestHeaderFieldsTooLarge = new Status(431, "Request Header Fields Too Large") with NoConstraints

  val InternalServerError = new Status(500, "Internal Server Error") with NoConstraints
  val NotImplemented = new Status(501, "Not Implemented") with NoConstraints
  val BadGateway = new Status(502, "Bad Gateway") with NoConstraints
  val ServiceUnavailable = new Status(503, "Service Unavailable") with NoConstraints
  val GatewayTimeout = new Status(504, "Gateway Timeout") with NoConstraints
  val HttpVersionNotSupported = new Status(505, "HTTP Version not supported") with NoConstraints
  val VariantAlsoNegotiates = new Status(506, "Variant Also Negotiates") with NoConstraints
  val InsufficientStorage = new Status(507, "Insufficient Storage") with NoConstraints
  val LoopDetected = new Status(508, "Loop Detected") with NoConstraints
  val NotExtended = new Status(510, "Not Extended") with NoConstraints
  val NetworkAuthenticationRequired = new Status(511, "Network Authentication Required") with NoConstraints
}
