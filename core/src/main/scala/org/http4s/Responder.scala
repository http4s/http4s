package org.http4s


import play.api.libs.iteratee._
import java.net.{URL, URI}

case class Responder(
  prelude: ResponsePrelude,
  body: ResponderBody = Responder.EmptyBody
)

object Responder {
  def replace[F, T](enumerator: Enumerator[T]): Enumeratee[F, T] = new Enumeratee[F, T] {
    def applyOn[A](inner: Iteratee[T, A]): Iteratee[F, Iteratee[T, A]] =
      Done(Iteratee.flatten(enumerator(inner)), Input.Empty)
  }

  val EmptyBody: Enumeratee[HttpChunk, HttpChunk] = replace(Enumerator.eof)

  implicit def responder2Handler(responder: Responder): Iteratee[HttpChunk, Responder] = Done(responder)
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
  trait NoEntityResponseGenerator { self: Status =>
    private[this] val StatusResponder = Responder(ResponsePrelude(this))
    def apply(): Responder = StatusResponder
  }

  trait EntityResponseGenerator extends NoEntityResponseGenerator { self: Status =>
    def apply[A](body: A)(implicit w: Writable[A]): Responder =
      feedChunk(Enumerator[HttpChunk](HttpEntity(w.asRaw(body))))

    def feedChunk(body: Enumerator[HttpChunk]): Responder =
      Responder(ResponsePrelude(this, Headers.Empty), Responder.replace(body))

    // Here is our ugly duckling
    def feed[A](body: Enumerator[A] = Enumerator.eof)(implicit w: Writable[A]): Responder =
      feedChunk(body.map(a => HttpEntity(w.asRaw(a))))

    def transform(enumeratee: Enumeratee[HttpChunk, HttpChunk]) =
      Responder(ResponsePrelude(this, Headers.Empty), Enumeratee.passAlong compose enumeratee)
  }

  trait RedirectResponseGenerator { self: Status =>
    def apply(uri: String): Responder = Responder(ResponsePrelude(
      status = this, headers = Headers(HttpHeaders.Location(uri))
    ))

    def apply(uri: URI): Responder = apply(uri.toString)

    def apply(url: URL): Responder = apply(url.toString)
  }

  /**
   * Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
   */
  object Continue extends Status(100, "Continue") with NoEntityResponseGenerator
  object SwitchingProtocols extends Status(101, "Switching Protocols") {
    // TODO type this header
    def apply(protocols: String, headers: Headers = Headers.Empty): Responder =
      Responder(ResponsePrelude(this, HttpHeaders.RawHeader("Upgrade", protocols) +: headers), Responder.EmptyBody)
  }
  object Processing extends Status(102, "Processing") with NoEntityResponseGenerator

  object Ok extends Status(200, "OK") with EntityResponseGenerator
  object Created extends Status(201, "Created") with EntityResponseGenerator
  object Accepted extends Status(202, "Accepted") with EntityResponseGenerator
  object NonAuthoritativeInformation extends Status(203, "Non-Authoritative Information") with EntityResponseGenerator
  object NoContent extends Status(204, "No Content") with NoEntityResponseGenerator
  object ResetContent extends Status(205, "Reset Content") with NoEntityResponseGenerator
  object PartialContent extends Status(206, "Partial Content") with EntityResponseGenerator {
    // TODO type this header
    def apply(range: String, body: ResponderBody, headers: Headers = Headers.Empty): Responder =
      Responder(ResponsePrelude(this, HttpHeaders.RawHeader("Range", range) +: headers), body)
  }
  object MultiStatus extends Status(207, "Multi-Status") with EntityResponseGenerator
  object AlreadyReported extends Status(208, "Already Reported") with EntityResponseGenerator
  object IMUsed extends Status(226, "IM Used") with EntityResponseGenerator

  object MultipleChoices extends Status(300, "Multiple Choices") with EntityResponseGenerator
  object MovedPermanently extends Status(301, "Moved Permanently") with RedirectResponseGenerator
  object Found extends Status(302, "Found") with RedirectResponseGenerator
  object SeeOther extends Status(303, "See Other") with RedirectResponseGenerator
  object NotModified extends Status(304, "Not Modified") with NoEntityResponseGenerator
  object UseProxy extends Status(305, "Use Proxy") with RedirectResponseGenerator
  object TemporaryRedirect extends Status(306, "Temporary Redirect") with RedirectResponseGenerator

  object BadRequest extends Status(400, "Bad Request") with EntityResponseGenerator
  object Unauthorized extends Status(401, "Unauthorized") with EntityResponseGenerator {
    // TODO type this header
    def apply(wwwAuthenticate: String, body: ResponderBody, headers: Headers = Headers.Empty): Responder =
      Responder(ResponsePrelude(this, HttpHeaders.RawHeader("WWW-Authenticate", wwwAuthenticate) +: headers), body)
  }
  object PaymentRequired extends Status(402, "Payment Required") with EntityResponseGenerator
  object Forbidden extends Status(403, "Forbidden") with EntityResponseGenerator
  object NotFound extends Status(404, "Not Found") with EntityResponseGenerator {
    def apply(request: RequestPrelude): Responder = apply(s"${request.pathInfo} not found")
  }
  object MethodNotAllowed extends Status(405, "Method Not Allowed") {
    def apply(allowed: TraversableOnce[Method], body: ResponderBody, headers: Headers = Headers.Empty): Responder =
      Responder(ResponsePrelude(this, HttpHeaders.RawHeader("Allowed", allowed.mkString(", ")) +: headers), body)
  }
  object NotAcceptable extends Status(406, "Not Acceptable") with EntityResponseGenerator
  object ProxyAuthenticationRequired extends Status(407, "Proxy Authentication Required") {
    // TODO type this header
    def apply(proxyAuthenticate: String, body: ResponderBody, headers: Headers = Headers.Empty): Responder =
      Responder(ResponsePrelude(this, HttpHeaders.RawHeader("Proxy-Authenticate", proxyAuthenticate) +: headers), body)
  }
  object RequestTimeOut extends Status(408, "Request Time-out") with EntityResponseGenerator
  object Conflict extends Status(409, "Conflict") with EntityResponseGenerator
  object Gone extends Status(410, "Gone") with EntityResponseGenerator
  object LengthRequred extends Status(411, "Length Required") with EntityResponseGenerator
  object PreconditionFailed extends Status(412, "Precondition Failed") with EntityResponseGenerator
  object RequestEntityTooLarge extends Status(413, "Request Entity Too Large") with EntityResponseGenerator
  object RequestUriTooLarge extends Status(414, "Request-URI Too Large") with EntityResponseGenerator
  object UnsupportedMediaType extends Status(415, "Unsupported Media Type") with EntityResponseGenerator
  object RequestedRangeNotSatisfiable extends Status(416, "Requested Range Not Satisfiable") with EntityResponseGenerator
  object ExpectationFailed extends Status(417, "ExpectationFailed") with EntityResponseGenerator
  object ImATeapot extends Status(418, "I'm a teapot") with EntityResponseGenerator
  object UnprocessableEntity extends Status(422, "Unprocessable Entity") with EntityResponseGenerator
  object Locked extends Status(423, "Locked") with EntityResponseGenerator
  object FailedDependency extends Status(424, "Failed Dependency") with EntityResponseGenerator
  object UnorderedCollection extends Status(425, "Unordered Collection") with EntityResponseGenerator
  object UpgradeRequired extends Status(426, "Upgrade Required") with EntityResponseGenerator
  object PreconditionRequired extends Status(428, "Precondition Required") with EntityResponseGenerator
  object TooManyRequests extends Status(429, "Too Many Requests") with EntityResponseGenerator
  object RequestHeaderFieldsTooLarge extends Status(431, "Request Header Fields Too Large") with EntityResponseGenerator

  object InternalServerError extends Status(500, "Internal Server Error") with EntityResponseGenerator {
    // TODO Bad in production.  Development mode?  Implicit renderer?
    def apply(t: Throwable): Responder = apply(s"${t.getMessage}\n\nStacktrace:\n${t.getStackTraceString}")
  }
  object NotImplemented extends Status(501, "Not Implemented") with EntityResponseGenerator
  object BadGateway extends Status(502, "Bad Gateway") with EntityResponseGenerator
  object ServiceUnavailable extends Status(503, "Service Unavailable") with EntityResponseGenerator
  object GatewayTimeOut extends Status(504, "Gateway Time-out") with EntityResponseGenerator
  object HttpVersionNotSupported extends Status(505, "HTTP Version not supported") with EntityResponseGenerator
  object VariantAlsoNegotiates extends Status(506, "Variant Also Negotiates") with EntityResponseGenerator
  object InsufficientStorage extends Status(507, "Insufficient Storage") with EntityResponseGenerator
  object LoopDetected extends Status(508, "Loop Detected") with EntityResponseGenerator
  object NotExtended extends Status(510, "Not Extended") with EntityResponseGenerator
  object NetworkAuthenticationRequired extends Status(511, "Network Authentication Required") with EntityResponseGenerator

  private[this] val ReasonMap = Map(
    (for {
      line <- getClass.getMethods
      if line.getReturnType.isAssignableFrom(classOf[Status]) && line.getParameterTypes.isEmpty
      status = line.invoke(this).asInstanceOf[Status]
    } yield status.code -> status.reason):_*
  )

  def apply(code: Int): Status =
    Status(code, ReasonMap.getOrElse(code, ""))
}
