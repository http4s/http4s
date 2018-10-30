package org.http4s

import cats.{Order, Show}
import org.http4s.Status.ResponseClass
import org.http4s.util.Renderable

/** Representation of the HTTP response code and reason
  *
  * '''Note: ''' the reason is not important to the protocol and is not considered in equality checks.
  *
  * @param code HTTP status code
  * @param reason reason for the response. eg, OK
  * @see [http://tools.ietf.org/html/rfc7231#section-6 RFC7231, Section 6]
  * @see [http://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml IANA Status Code Registry]
  */
sealed abstract case class Status private (code: Int)(
    val reason: String,
    val isEntityAllowed: Boolean)
    extends Ordered[Status]
    with Renderable {
  // scalastyle:off magic.number
  val responseClass: ResponseClass =
    if (code < 200) Status.Informational
    else if (code < 300) Status.Successful
    else if (code < 400) Status.Redirection
    else if (code < 500) Status.ClientError
    else Status.ServerError
  // scalastyle:on magic.number

  def compare(that: Status): Int = code - that.code

  def isSuccess: Boolean = responseClass.isSuccess

  def withReason(reason: String): Status = Status(code, reason, isEntityAllowed)

  override def render(writer: org.http4s.util.Writer): writer.type = writer << code << ' ' << reason

  /** Helpers for for matching against a [[Response]] */
  def unapply[F[_]](msg: Response[F]): Option[Response[F]] =
    if (msg.status == this) Some(msg) else None
}

object Status {
  import Registry._

  def apply(code: Int, reason: String = "", isEntityAllowed: Boolean = true): Status =
    new Status(code)(reason, isEntityAllowed) {}

  sealed trait ResponseClass {
    def isSuccess: Boolean

    /** Match a [[Response]] based on [[Status]] category */
    final def unapply[F[_]](resp: Response[F]): Option[Response[F]] =
      resp match {
        case Response(status, _, _, _, _) if status.responseClass == this => Some(resp)
        case _ => None
      }
  }

  case object Informational extends ResponseClass { val isSuccess = true }
  case object Successful extends ResponseClass { val isSuccess = true }
  case object Redirection extends ResponseClass { val isSuccess = true }
  case object ClientError extends ResponseClass { val isSuccess = false }
  case object ServerError extends ResponseClass { val isSuccess = false }

  object ResponseClass {
    @deprecated("Moved to org.http4s.Status.Informational", "0.16")
    val Informational = Status.Informational
    @deprecated("Moved to org.http4s.Status.Successful", "0.16")
    val Successful = Status.Successful
    @deprecated("Moved to org.http4s.Status.Redirection", "0.16")
    val Redirection = Status.Informational
    @deprecated("Moved to org.http4s.Status.ClientError", "0.16")
    val ClientError = Status.Informational
    @deprecated("Moved to org.http4s.Status.ServerError", "0.16")
    val ServerError = Status.Informational
  }

  private[http4s] val MinCode = 100
  private[http4s] val MaxCode = 599

  def fromInt(code: Int): ParseResult[Status] = withRangeCheck(code) {
    lookup(code) match {
      case right: Right[_, _] => right
      case _ => ParseResult.success(Status(code, ""))
    }
  }

  def fromIntAndReason(code: Int, reason: String): ParseResult[Status] = withRangeCheck(code) {
    lookup(code, reason) match {
      case right: Right[_, _] => right
      case _ => ParseResult.success(Status(code, reason))
    }
  }

  private def withRangeCheck(code: Int)(onSuccess: => ParseResult[Status]): ParseResult[Status] =
    if (code >= MinCode && code <= MaxCode) onSuccess
    else ParseResult.fail("Invalid code", s"$code is not between $MinCode and $MaxCode.")

  private[http4s] def registered: List[Status] = all

  private object Registry {
    private val registry: Array[ParseResult[Status]] =
      Array.fill[ParseResult[Status]](MaxCode + 1) {
        ParseResult.fail("Unregistered", "Unregistered")
      }

    def lookup(code: Int): ParseResult[Status] = registry(code)

    def lookup(code: Int, reason: String): ParseResult[Status] = {
      val lookupResult = lookup(code)
      if (lookupResult.isRight && lookupResult.right.get.reason == reason) lookupResult
      else ParseResult.fail("Reason did not match", s"Nonstandard reason: $reason")
    }

    def register(status: Status): Status = {
      registry(status.code) = Right(status)
      status
    }

    def all: List[Status] = registry.filter(_.isRight).map(_.right.get).toList
  }

  /**
    * Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
    */
  // scalastyle:off magic.number
  val Continue: Status = register(Status(100, "Continue", isEntityAllowed = false))
  val SwitchingProtocols: Status = register(
    Status(101, "Switching Protocols", isEntityAllowed = false))
  val Processing: Status = register(Status(102, "Processing", isEntityAllowed = false))
  val EarlyHints: Status = register(Status(103, "Early Hints", isEntityAllowed = false))

  val Ok: Status = register(Status(200, "OK"))
  val Created: Status = register(Status(201, "Created"))
  val Accepted: Status = register(Status(202, "Accepted"))
  val NonAuthoritativeInformation: Status = register(Status(203, "Non-Authoritative Information"))
  val NoContent: Status = register(Status(204, "No Content", isEntityAllowed = false))
  val ResetContent: Status = register(Status(205, "Reset Content", isEntityAllowed = false))
  val PartialContent: Status = register(Status(206, "Partial Content"))
  val MultiStatus: Status = register(Status(207, "Multi-Status"))
  val AlreadyReported: Status = register(Status(208, "Already Reported"))
  val IMUsed: Status = register(Status(226, "IM Used"))

  val MultipleChoices: Status = register(Status(300, "Multiple Choices"))
  val MovedPermanently: Status = register(Status(301, "Moved Permanently"))
  val Found: Status = register(Status(302, "Found"))
  val SeeOther: Status = register(Status(303, "See Other"))
  val NotModified: Status = register(Status(304, "Not Modified", isEntityAllowed = false))
  val UseProxy: Status = register(Status(305, "Use Proxy"))
  val TemporaryRedirect: Status = register(Status(307, "Temporary Redirect"))
  val PermanentRedirect: Status = register(Status(308, "Permanent Redirect"))

  val BadRequest: Status = register(Status(400, "Bad Request"))
  val Unauthorized: Status = register(Status(401, "Unauthorized"))
  val PaymentRequired: Status = register(Status(402, "Payment Required"))
  val Forbidden: Status = register(Status(403, "Forbidden"))
  val NotFound: Status = register(Status(404, "Not Found"))
  val MethodNotAllowed: Status = register(Status(405, "Method Not Allowed"))
  val NotAcceptable: Status = register(Status(406, "Not Acceptable"))
  val ProxyAuthenticationRequired: Status = register(Status(407, "Proxy Authentication Required"))
  val RequestTimeout: Status = register(Status(408, "Request Timeout"))
  val Conflict: Status = register(Status(409, "Conflict"))
  val Gone: Status = register(Status(410, "Gone"))
  val LengthRequired: Status = register(Status(411, "Length Required"))
  val PreconditionFailed: Status = register(Status(412, "Precondition Failed"))
  val PayloadTooLarge: Status = register(Status(413, "Payload Too Large"))
  val UriTooLong: Status = register(Status(414, "URI Too Long"))
  val UnsupportedMediaType: Status = register(Status(415, "Unsupported Media Type"))
  val RangeNotSatisfiable: Status = register(Status(416, "Range Not Satisfiable"))
  val ExpectationFailed: Status = register(Status(417, "Expectation Failed"))
  val MisdirectedRequest: Status = register(Status(421, "Misdirected Request"))
  val UnprocessableEntity: Status = register(Status(422, "Unprocessable Entity"))
  val Locked: Status = register(Status(423, "Locked"))
  val FailedDependency: Status = register(Status(424, "Failed Dependency"))
  val TooEarly: Status = register(Status(425, "Too Early"))
  val UpgradeRequired: Status = register(Status(426, "Upgrade Required"))
  val PreconditionRequired: Status = register(Status(428, "Precondition Required"))
  val TooManyRequests: Status = register(Status(429, "Too Many Requests"))
  val RequestHeaderFieldsTooLarge: Status = register(Status(431, "Request Header Fields Too Large"))
  val UnavailableForLegalReasons: Status = register(Status(451, "Unavailable For Legal Reasons"))

  val InternalServerError: Status = register(Status(500, "Internal Server Error"))
  val NotImplemented: Status = register(Status(501, "Not Implemented"))
  val BadGateway: Status = register(Status(502, "Bad Gateway"))
  val ServiceUnavailable: Status = register(Status(503, "Service Unavailable"))
  val GatewayTimeout: Status = register(Status(504, "Gateway Timeout"))
  val HttpVersionNotSupported: Status = register(Status(505, "HTTP Version not supported"))
  val VariantAlsoNegotiates: Status = register(Status(506, "Variant Also Negotiates"))
  val InsufficientStorage: Status = register(Status(507, "Insufficient Storage"))
  val LoopDetected: Status = register(Status(508, "Loop Detected"))
  val NotExtended: Status = register(Status(510, "Not Extended"))
  val NetworkAuthenticationRequired: Status = register(
    Status(511, "Network Authentication Required"))
  // scalastyle:on magic.number

  implicit val http4sOrderForStatus: Order[Status] = Order.fromOrdering[Status]
  implicit val http4sShowForStatus: Show[Status] = Show.fromToString[Status]
}
