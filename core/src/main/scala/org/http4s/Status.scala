package org.http4s

import java.util.concurrent.atomic.AtomicReferenceArray
import scalaz._

import Status.ResponseClass
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
final case class Status private (code: Int)(val reason: String = "", val isEntityAllowed: Boolean = true) extends Ordered[Status] with Renderable {
  val responseClass: ResponseClass =
    if (code < 200) ResponseClass.Informational
    else if (code < 300) ResponseClass.Successful
    else if (code < 400) ResponseClass.Redirection
    else if (code < 500) ResponseClass.ClientError
    else ResponseClass.ServerError

  def compare(that: Status) = code - that.code

  def isSuccess = responseClass.isSuccess

  def withReason(reason: String) = new Status(code)(reason, isEntityAllowed)

  override def render(writer: org.http4s.util.Writer): writer.type =  writer << code << ' ' << reason

  /** Helpers for for matching against a [[Response]] */
  def unapply(msg: Response): Option[Response] = {
    if (msg.status == this) Some(msg) else None
  }
}

object Status {
  sealed trait ResponseClass {
    def isSuccess: Boolean

    /** Match a [[Response]] based on [[Status]] category */
    final def unapply(resp: Response): Option[Response] =
      if (resp.status.responseClass == this) Some(resp) else None
  }

  object ResponseClass {
    case object Informational extends ResponseClass { val isSuccess = true }
    case object Successful extends ResponseClass { val isSuccess = true }
    case object Redirection extends ResponseClass { val isSuccess = true }
    case object ClientError extends ResponseClass { val isSuccess = false }
    case object ServerError extends ResponseClass { val isSuccess = false }
  }

  private def mkStatus(code: Int, reason: String = ""): ParseResult[Status] =
    if (code >= 100 && code <= 599) ParseResult.success(Status(code)(isEntityAllowed = true))
    else ParseResult.fail("Invalid status", s"Code $code must be between 100 and 599, inclusive")

  private def lookup(code: Int): Option[\/-[Status]] =
    if (code < 100 || code > 599) None else Option(registry.get(code))

  def fromInt(code: Int): ParseResult[Status] = lookup(code).getOrElse(mkStatus(code))

  def fromIntAndReason(code: Int, reason: String): ParseResult[Status] =
    lookup(code).filter(_.b.reason == reason).getOrElse(mkStatus(code, reason))

  private val registry = new AtomicReferenceArray[\/-[Status]](600)

  def registered: Iterable[Status] = for {
    code <- 100 to 599
    status <- Option(registry.get(code)).map(_.b)
  } yield status

  def register(status: Status): status.type = {
    registry.set(status.code, \/-(status))
    status
  }

  /**
   * Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
   */
  val Continue = register(Status(100)("Continue", isEntityAllowed = false))
  val SwitchingProtocols = register(Status(101)("Switching Protocols", isEntityAllowed = false))
  val Processing = register(Status(102)("Processing", isEntityAllowed = false))

  val Ok = register(Status(200)("OK"))
  val Created = register(Status(201)("Created"))
  val Accepted = register(Status(202)("Accepted"))
  val NonAuthoritativeInformation = register(Status(203)("Non-Authoritative Information"))
  val NoContent = register(Status(204)("No Content", isEntityAllowed = false))
  val ResetContent = register(Status(205)("Reset Content", isEntityAllowed = false))
  val PartialContent = register(Status(206)("Partial Content"))
  val MultiStatus = register(Status(207)("Multi-Status"))
  val AlreadyReported = register(Status(208)("Already Reported"))
  val IMUsed = register(Status(226)("IM Used"))

  val MultipleChoices = register(Status(300)("Multiple Choices"))
  val MovedPermanently = register(Status(301)("Moved Permanently"))
  val Found = register(Status(302)("Found"))
  val SeeOther = register(Status(303)("See Other"))
  val NotModified = register(Status(304)("Not Modified", isEntityAllowed = false))
  val UseProxy = register(Status(305)("Use Proxy"))
  val TemporaryRedirect = register(Status(307)("Temporary Redirect"))
  val PermanentRedirect = register(Status(308)("Permanent Redirect"))

  val BadRequest = register(Status(400)("Bad Request"))
  val Unauthorized = register(Status(401)("Unauthorized"))
  val PaymentRequired = register(Status(402)("Payment Required"))
  val Forbidden = register(Status(403)("Forbidden"))
  val NotFound = register(Status(404)("Not Found"))
  val MethodNotAllowed = register(Status(405)("Method Not Allowed"))
  val NotAcceptable = register(Status(406)("Not Acceptable"))
  val ProxyAuthenticationRequired = register(Status(407)("Proxy Authentication Required"))
  val RequestTimeout = register(Status(408)("Request Timeout"))
  val Conflict = register(Status(409)("Conflict"))
  val Gone = register(Status(410)("Gone"))
  val LengthRequired = register(Status(411)("Length Required"))
  val PreconditionFailed = register(Status(412)("Precondition Failed"))
  val PayloadTooLarge = register(Status(413)("Payload Too Large"))
  val UriTooLong = register(Status(414)("URI Too Long"))
  val UnsupportedMediaType = register(Status(415)("Unsupported Media Type"))
  val RangeNotSatisfiable = register(Status(416)("Range Not Satisfiable"))
  val ExpectationFailed = register(Status(417)("Expectation Failed"))
  val UnprocessableEntity = register(Status(422)("Unprocessable Entity"))
  val Locked = register(Status(423)("Locked"))
  val FailedDependency = register(Status(424)("Failed Dependency"))
  val UpgradeRequired = register(Status(426)("Upgrade Required"))
  val PreconditionRequired = register(Status(428)("Precondition Required"))
  val TooManyRequests = register(Status(429)("Too Many Requests"))
  val RequestHeaderFieldsTooLarge = register(Status(431)("Request Header Fields Too Large"))
  val UnavailableForLegalReasons = register(Status(451)("Unavailable For Legal Reasons"))

  val InternalServerError = register(Status(500)("Internal Server Error"))
  val NotImplemented = register(Status(501)("Not Implemented"))
  val BadGateway = register(Status(502)("Bad Gateway"))
  val ServiceUnavailable = register(Status(503)("Service Unavailable"))
  val GatewayTimeout = register(Status(504)("Gateway Timeout"))
  val HttpVersionNotSupported = register(Status(505)("HTTP Version not supported"))
  val VariantAlsoNegotiates = register(Status(506)("Variant Also Negotiates"))
  val InsufficientStorage = register(Status(507)("Insufficient Storage"))
  val LoopDetected = register(Status(508)("Loop Detected"))
  val NotExtended = register(Status(510)("Not Extended"))
  val NetworkAuthenticationRequired = register(Status(511)("Network Authentication Required"))
}

trait StatusInstances {
  implicit val StatusShow = Show.showFromToString[Status]
  implicit val StatusOrder = Order.fromScalaOrdering[Status]
}
