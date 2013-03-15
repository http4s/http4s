package org.http4s


import play.api.libs.iteratee._
import java.net.{URL, URI}
import reflect.ClassTag
import util.DateTime


case class Responder(
  prelude: ResponsePrelude,
  body: ResponderBody = Responder.EmptyBody
) {
  def contentType: Option[ContentType] =  Responder.getContentType(this)
  def contentType(contentType: ContentType) = Responder.setContentType(this, contentType)
  def addCookie(cookie: HttpCookie) = Responder.addCookie(this, cookie)
  def removeCookie(cookie: HttpCookie) = Responder.removeCookie(this, cookie)
  def addHeader(header: HttpHeader) = Responder.addHeader(this, header)
  def status = Responder.getStatus(this)
  def status[T <% Status](status: T) = Responder.setStatus(this, status)
}

object Responder {
  val EmptyBody: Enumeratee[HttpChunk, HttpChunk] = Enumeratee.heading(Enumerator.eof)

  implicit def responder2Handler(responder: Responder): Iteratee[HttpChunk, Responder] = Done(responder)

  import shapeless._
  import Lens._
  import Nat._

  implicit val responsePreludeIso = Iso.hlist(ResponsePrelude.apply _, ResponsePrelude.unapply _)
  implicit val statusIso = Iso.hlist(Status.apply(_: Int, _: String), Status.unapply _)
  implicit val responderIso = Iso.hlist(Responder.apply _, Responder.unapply _)

  val headersLens = Lens[Responder] >> _0 >> _1
  val statusLens = Lens[Responder] >> _0 >> _0

  def addCookie(responder: Responder, cookie: HttpCookie) = {
    addHeader(responder, HttpHeaders.`Set-Cookie`(cookie))
  }

  def removeCookie(responder: Responder, cookie: HttpCookie) = {
    addHeader(responder, HttpHeaders.`Set-Cookie`(cookie.copy(content = "", expires = Some(DateTime(0)), maxAge = Some(0))))
  }

  def addHeader(responder: Responder, header: HttpHeader) = {
    headersLens.modify(responder)(_ :+ header)
  }

  def setContentType(responder: Responder, contentType: ContentType) = {
    headersLens.modify(responder)(_ :+ HttpHeaders.`Content-Type`(contentType))
  }

  def getContentType(responder: Responder): Option[ContentType] =
    (headersLens.get(responder).get("Content-Type").map(_.parsed) collect {
      case HttpHeaders.`Content-Type`(contentType) => contentType
    })

  def getStatus(responder: Responder) = statusLens.get(responder)
  def setStatus(responder: Responder, status: Status) = statusLens.set(responder)(status)
}

case class Status(code: Int, reason: String) extends Ordered[Status] {
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
  trait NoEntityResponderGenerator { self: Status =>
    private[this] val StatusResponder = Responder(ResponsePrelude(this))
    def apply(): Responder = StatusResponder
  }

  trait EntityResponderGenerator extends NoEntityResponderGenerator { self: Status =>
    def apply[A](body: A)(implicit w: Writable[A]): Responder =
      apply(body, w.contentType)(w)

    def apply[A](body: A, contentType: ContentType)(implicit w: Writable[A]) = {
      var headers = HttpHeaders.Empty
      val (parsedBody, length) = w.toBody(body)
      headers :+= HttpHeaders.`Content-Type`(contentType)
      length.foreach{ length => headers :+= HttpHeaders.`Content-Length`(length) }
      Responder(ResponsePrelude(self, headers), parsedBody)
    }
  }

  trait RedirectResponderGenerator { self: Status =>
    def apply(uri: String): Responder = Responder(ResponsePrelude(self, HttpHeaders(HttpHeaders.Location(uri))))

    def apply(uri: URI): Responder = apply(uri.toString)

    def apply(url: URL): Responder = apply(url.toString)
  }

  /**
   * Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
   */
  object Continue extends Status(100, "Continue") with NoEntityResponderGenerator
  object SwitchingProtocols extends Status(101, "Switching Protocols") {
    // TODO type this header
    def apply(protocols: String, headers: HttpHeaders = HttpHeaders.Empty): Responder =
      Responder(ResponsePrelude(this, HttpHeaders.RawHeader("Upgrade", protocols) +: headers), Responder.EmptyBody)
  }
  object Processing extends Status(102, "Processing") with NoEntityResponderGenerator

  object Ok extends Status(200, "OK") with EntityResponderGenerator
  object Created extends Status(201, "Created") with EntityResponderGenerator
  object Accepted extends Status(202, "Accepted") with EntityResponderGenerator
  object NonAuthoritativeInformation extends Status(203, "Non-Authoritative Information") with EntityResponderGenerator
  object NoContent extends Status(204, "No Content") with NoEntityResponderGenerator
  object ResetContent extends Status(205, "Reset Content") with NoEntityResponderGenerator
  object PartialContent extends Status(206, "Partial Content") with EntityResponderGenerator {
    // TODO type this header
    def apply(range: String, body: ResponderBody, headers: HttpHeaders = HttpHeaders.Empty): Responder =
      Responder(ResponsePrelude(this, HttpHeaders.RawHeader("Range", range) +: headers), body)
  }
  object MultiStatus extends Status(207, "Multi-Status") with EntityResponderGenerator
  object AlreadyReported extends Status(208, "Already Reported") with EntityResponderGenerator
  object IMUsed extends Status(226, "IM Used") with EntityResponderGenerator

  object MultipleChoices extends Status(300, "Multiple Choices") with EntityResponderGenerator
  object MovedPermanently extends Status(301, "Moved Permanently") with RedirectResponderGenerator
  object Found extends Status(302, "Found") with RedirectResponderGenerator
  object SeeOther extends Status(303, "See Other") with RedirectResponderGenerator
  object NotModified extends Status(304, "Not Modified") with NoEntityResponderGenerator
  object UseProxy extends Status(305, "Use Proxy") with RedirectResponderGenerator
  object TemporaryRedirect extends Status(306, "Temporary Redirect") with RedirectResponderGenerator

  object BadRequest extends Status(400, "Bad Request") with EntityResponderGenerator
  object Unauthorized extends Status(401, "Unauthorized") with EntityResponderGenerator {
    // TODO type this header
    def apply(wwwAuthenticate: String, body: ResponderBody, headers: HttpHeaders = HttpHeaders.Empty): Responder =
      Responder(ResponsePrelude(this, HttpHeaders.RawHeader("WWW-Authenticate", wwwAuthenticate) +: headers), body)
  }
  object PaymentRequired extends Status(402, "Payment Required") with EntityResponderGenerator
  object Forbidden extends Status(403, "Forbidden") with EntityResponderGenerator
  object NotFound extends Status(404, "Not Found") with EntityResponderGenerator {
    def apply(request: RequestPrelude): Responder = apply(s"${request.pathInfo} not found")
  }
  object MethodNotAllowed extends Status(405, "Method Not Allowed") {
    def apply(allowed: TraversableOnce[Method], body: ResponderBody, headers: HttpHeaders = HttpHeaders.Empty): Responder =
      Responder(ResponsePrelude(this, HttpHeaders.RawHeader("Allowed", allowed.mkString(", ")) +: headers), body)
  }
  object NotAcceptable extends Status(406, "Not Acceptable") with EntityResponderGenerator
  object ProxyAuthenticationRequired extends Status(407, "Proxy Authentication Required") {
    // TODO type this header
    def apply(proxyAuthenticate: String, body: ResponderBody, headers: HttpHeaders = HttpHeaders.Empty): Responder =
      Responder(ResponsePrelude(this, HttpHeaders.RawHeader("Proxy-Authenticate", proxyAuthenticate) +: headers), body)
  }
  object RequestTimeOut extends Status(408, "Request Time-out") with EntityResponderGenerator
  object Conflict extends Status(409, "Conflict") with EntityResponderGenerator
  object Gone extends Status(410, "Gone") with EntityResponderGenerator
  object LengthRequred extends Status(411, "Length Required") with EntityResponderGenerator
  object PreconditionFailed extends Status(412, "Precondition Failed") with EntityResponderGenerator
  object RequestEntityTooLarge extends Status(413, "Request Entity Too Large") with EntityResponderGenerator
  object RequestUriTooLarge extends Status(414, "Request-URI Too Large") with EntityResponderGenerator
  object UnsupportedMediaType extends Status(415, "Unsupported Media Type") with EntityResponderGenerator
  object RequestedRangeNotSatisfiable extends Status(416, "Requested Range Not Satisfiable") with EntityResponderGenerator
  object ExpectationFailed extends Status(417, "ExpectationFailed") with EntityResponderGenerator
  object ImATeapot extends Status(418, "I'm a teapot") with EntityResponderGenerator
  object UnprocessableEntity extends Status(422, "Unprocessable Entity") with EntityResponderGenerator
  object Locked extends Status(423, "Locked") with EntityResponderGenerator
  object FailedDependency extends Status(424, "Failed Dependency") with EntityResponderGenerator
  object UnorderedCollection extends Status(425, "Unordered Collection") with EntityResponderGenerator
  object UpgradeRequired extends Status(426, "Upgrade Required") with EntityResponderGenerator
  object PreconditionRequired extends Status(428, "Precondition Required") with EntityResponderGenerator
  object TooManyRequests extends Status(429, "Too Many Requests") with EntityResponderGenerator
  object RequestHeaderFieldsTooLarge extends Status(431, "Request Header Fields Too Large") with EntityResponderGenerator

  object InternalServerError extends Status(500, "Internal Server Error") with EntityResponderGenerator {
    // TODO Bad in production.  Development mode?  Implicit renderer?
    def apply(t: Throwable): Responder = apply(s"${t.getMessage}\n\nStacktrace:\n${t.getStackTraceString}")
  }
  object NotImplemented extends Status(501, "Not Implemented") with EntityResponderGenerator
  object BadGateway extends Status(502, "Bad Gateway") with EntityResponderGenerator
  object ServiceUnavailable extends Status(503, "Service Unavailable") with EntityResponderGenerator
  object GatewayTimeOut extends Status(504, "Gateway Time-out") with EntityResponderGenerator
  object HttpVersionNotSupported extends Status(505, "HTTP Version not supported") with EntityResponderGenerator
  object VariantAlsoNegotiates extends Status(506, "Variant Also Negotiates") with EntityResponderGenerator
  object InsufficientStorage extends Status(507, "Insufficient Storage") with EntityResponderGenerator
  object LoopDetected extends Status(508, "Loop Detected") with EntityResponderGenerator
  object NotExtended extends Status(510, "Not Extended") with EntityResponderGenerator
  object NetworkAuthenticationRequired extends Status(511, "Network Authentication Required") with EntityResponderGenerator

  private[this] val ReasonMap = Map(
    (for {
      line <- getClass.getMethods
      if line.getReturnType.isAssignableFrom(classOf[Status]) && line.getParameterTypes.isEmpty
      status = line.invoke(this).asInstanceOf[Status]
    } yield status.code -> status.reason):_*
  )

  def apply(code: Int): Status =
    Status(code, ReasonMap.getOrElse(code, ""))

  implicit def int2statusCode(i: Int): Status = apply(i)
  implicit def tuple2statusCode(tup: (Int, String)) = apply(tup._1, tup._2)
}
