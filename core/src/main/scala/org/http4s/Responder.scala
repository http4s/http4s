package org.http4s


import play.api.libs.iteratee._
import util.FastEnumerator


case class Responder(
  prelude: ResponsePrelude,
  body: Responder.Body = Responder.EmptyBody) {

  def body[A](body: A)(implicit w: Writable[A]): Responder =
    copy(body = Responder.replace(FastEnumerator(w.toChunk(body))))

  def feed[A](enumerator: Enumerator[A])(implicit w: Writable[A]): Responder =
    copy(body = Responder.replace(enumerator.map(w.toChunk)))
}

object Responder {
  type Body = Enumeratee[HttpChunk, HttpChunk]

  def replace[F, T](enumerator: Enumerator[T]): Enumeratee[F, T] = new Enumeratee[F, T] {
    def applyOn[A](inner: Iteratee[T, A]): Iteratee[F, Iteratee[T, A]] =
      Done(Iteratee.flatten(enumerator(inner)), Input.Empty)
  }

  val EmptyBody: Enumeratee[HttpChunk, HttpChunk] = replace(Enumerator.eof)

  implicit def responder2Handler(responder: Responder): Iteratee[HttpChunk, Responder] = Done(responder)
}

case class StatusLine(code: Int, reason: String) extends Ordered[StatusLine] {
  def apply(): Responder = Responder(ResponsePrelude(this, Headers.Empty), Responder.EmptyBody)

  def apply[A](body: A)(implicit w: Writable[A]): Responder = feedChunk(FastEnumerator(w.toChunk(body)))

  def feedChunk(body: Enumerator[HttpChunk]): Responder =
    Responder(ResponsePrelude(this, Headers.Empty), Responder.replace(body))

  // Here is our ugly duckling
  def feed[A](body: Enumerator[A] = Enumerator.eof)(implicit w: Writable[A]): Responder =
    feedChunk(body.map(w.toChunk(_)))

  def transform(enumeratee: Enumeratee[HttpChunk, HttpChunk]) =
    Responder(ResponsePrelude(this, Headers.Empty), Enumeratee.passAlong compose enumeratee)

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
    (for {
      line <- getClass.getMethods
      if line.getReturnType.isAssignableFrom(classOf[StatusLine]) && line.getParameterTypes.isEmpty
      status = line.invoke(this).asInstanceOf[StatusLine]
    } yield status.code -> status.reason):_*
  )


  def apply(code: Int): StatusLine =
    StatusLine(code, ReasonMap.getOrElse(code, ""))

}

object ResponderGenerators {
  import StatusLine._

  def genRouteErrorResponse(t: Throwable): Responder = {
    InternalServerError(s"${t.getMessage}\n\nStacktrace:\n${t.getStackTraceString}")
  }

  def genRouteNotFound(request: RequestPrelude): Responder = {
    NotFound(s"${request.pathInfo} Not Found.")
  }
}
