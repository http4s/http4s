package org.http4s

import cats._
import org.http4s.Status.ResponseClass
import org.http4s.util.Renderable

/** Representation of the HTTP response code and reason
  *
  * '''Note: ''' the reason is not important to the protocol and is not considered in equality checks.
  *
  * @param code HTTP status code
  * @param reason reason for the response. eg, OK
  *
  * @see [http://tools.ietf.org/html/rfc7231#section-6 RFC7231, Section 6]
  * @see [http://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml IANA Status Code Registry]
  */
sealed abstract case class Status private (code: Int)(val reason: String, val isEntityAllowed: Boolean)
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

  def withReason(reason: String): Status = new Status(code)(reason, isEntityAllowed) {}

  override def render(writer: org.http4s.util.Writer): writer.type = writer << code << ' ' << reason

  /** Helpers for for matching against a [[Response]] */
  def unapply[F[_]](msg: Response[F]): Option[Response[F]] =
    if (msg.status == this) Some(msg) else None
}

object Status {
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

  val MinCode = 100
  val MaxCode = 599

  private def registerNew(code: Int, reason: String, isEntityAllowed: Boolean = true): Status = {
    val status = makeNew(code, reason, isEntityAllowed)
    registry(code) = Some(status)
    status
  }

  private def makeNew(code: Int, reason: String, isEntityAllowed: Boolean = true) = {
    new Status(code)(reason, isEntityAllowed) {}
  }

  private def parseAsStatus(code: Int, reason: String = ""): ParseResult[Status] =
    if (isInRange(code)) ParseResult.success(makeNew(code, reason))
    else ParseResult.fail("Invalid status", s"Code $code must be between 100 and 599, inclusive")

  private def isInRange(code: Int) = code >= MinCode && code <= MaxCode

  def fromInt(code: Int): ParseResult[Status] = lookup(code) match {
    case None => parseAsStatus(code)
    case Some(status) => Right(status)
  }

  private def lookup(code: Int) = {
    if (isInRange(code)) registry(code) else None
  }
  def fromIntAndReason(code: Int, reason: String): ParseResult[Status] = lookup(code) match {
    case None => parseAsStatus(code, reason)
    case Some(status) => if (status.reason == reason) Right(status) else parseAsStatus(code, reason)
  }

  private val registry = Array.fill[Option[Status]](MaxCode + 1)(None)

  def registered: Iterable[Status] =
    for {
      code <- MinCode to MaxCode
      status <- lookup(code)
    } yield status

  /**
    * Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
    */
  // scalastyle:off magic.number
  val Continue : Status = registerNew(100, "Continue", isEntityAllowed = false)
  val SwitchingProtocols : Status = registerNew(101, "Switching Protocols", isEntityAllowed = false)
  val Processing : Status = registerNew(102, "Processing", isEntityAllowed = false)

  val Ok : Status = registerNew(200, "OK")
  val Created : Status = registerNew(201, "Created")
  val Accepted : Status = registerNew(202, "Accepted")
  val NonAuthoritativeInformation : Status = registerNew(203, "Non-Authoritative Information")
  val NoContent : Status = registerNew(204, "No Content", isEntityAllowed = false)
  val ResetContent : Status = registerNew(205, "Reset Content", isEntityAllowed = false)
  val PartialContent : Status = registerNew(206, "Partial Content")
  val MultiStatus : Status = registerNew(207, "Multi-Status")
  val AlreadyReported : Status = registerNew(208, "Already Reported")
  val IMUsed : Status = registerNew(226, "IM Used")

  val MultipleChoices : Status = registerNew(300, "Multiple Choices")
  val MovedPermanently : Status = registerNew(301, "Moved Permanently")
  val Found : Status = registerNew(302, "Found")
  val SeeOther : Status = registerNew(303, "See Other")
  val NotModified : Status = registerNew(304, "Not Modified", isEntityAllowed = false)
  val UseProxy : Status = registerNew(305, "Use Proxy")
  val TemporaryRedirect : Status = registerNew(307, "Temporary Redirect")
  val PermanentRedirect : Status = registerNew(308, "Permanent Redirect")

  val BadRequest : Status = registerNew(400, "Bad Request")
  val Unauthorized : Status = registerNew(401, "Unauthorized")
  val PaymentRequired : Status = registerNew(402, "Payment Required")
  val Forbidden : Status = registerNew(403, "Forbidden")
  val NotFound : Status = registerNew(404, "Not Found")
  val MethodNotAllowed : Status = registerNew(405, "Method Not Allowed")
  val NotAcceptable : Status = registerNew(406, "Not Acceptable")
  val ProxyAuthenticationRequired : Status = registerNew(407, "Proxy Authentication Required")
  val RequestTimeout : Status = registerNew(408, "Request Timeout")
  val Conflict : Status = registerNew(409, "Conflict")
  val Gone : Status = registerNew(410, "Gone")
  val LengthRequired : Status = registerNew(411, "Length Required")
  val PreconditionFailed : Status = registerNew(412, "Precondition Failed")
  val PayloadTooLarge : Status = registerNew(413, "Payload Too Large")
  val UriTooLong : Status = registerNew(414, "URI Too Long")
  val UnsupportedMediaType : Status = registerNew(415, "Unsupported Media Type")
  val RangeNotSatisfiable : Status = registerNew(416, "Range Not Satisfiable")
  val ExpectationFailed : Status = registerNew(417, "Expectation Failed")
  val UnprocessableEntity : Status = registerNew(422, "Unprocessable Entity")
  val Locked : Status = registerNew(423, "Locked")
  val FailedDependency : Status = registerNew(424, "Failed Dependency")
  val UpgradeRequired : Status = registerNew(426, "Upgrade Required")
  val PreconditionRequired : Status = registerNew(428, "Precondition Required")
  val TooManyRequests : Status = registerNew(429, "Too Many Requests")
  val RequestHeaderFieldsTooLarge : Status = registerNew(431, "Request Header Fields Too Large")
  val UnavailableForLegalReasons : Status = registerNew(451, "Unavailable For Legal Reasons")

  val InternalServerError : Status = registerNew(500, "Internal Server Error")
  val NotImplemented : Status = registerNew(501, "Not Implemented")
  val BadGateway : Status = registerNew(502, "Bad Gateway")
  val ServiceUnavailable : Status = registerNew(503, "Service Unavailable")
  val GatewayTimeout : Status = registerNew(504, "Gateway Timeout")
  val HttpVersionNotSupported : Status = registerNew(505, "HTTP Version not supported")
  val VariantAlsoNegotiates : Status = registerNew(506, "Variant Also Negotiates")
  val InsufficientStorage : Status = registerNew(507, "Insufficient Storage")
  val LoopDetected : Status = registerNew(508, "Loop Detected")
  val NotExtended : Status = registerNew(510, "Not Extended")
  val NetworkAuthenticationRequired : Status = registerNew(511, "Network Authentication Required")
  // scalastyle:on magic.number
}

trait StatusInstances {
  implicit val StatusShow = Show.fromToString[Status]
  implicit val StatusOrder = Order.fromOrdering[Status]
}
