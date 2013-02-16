package org.http4s


import play.api.libs.iteratee._


case class Responder(
  prelude: ResponsePrelude,
  body: Responder.Body = Responder.EmptyBody) {

  def body[A](body: A)(implicit w: Writable[A]): Responder =
    copy(body = Responder.replace(Enumerator(w.toChunk(body))))

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
  class EmptyBodyStatusLine(code: Int, reason: String) extends StatusLine(code, reason) {
    def apply(): Responder = Responder(ResponsePrelude(this, Headers.Empty), Responder.EmptyBody)
  }

  class StatusLineWithBody(code: Int, reason: String) extends EmptyBodyStatusLine(code, reason) {
    def apply[A](body: A)(implicit w: Writable[A]): Responder = feedChunk(Enumerator(w.toChunk(body)))

    def feedChunk(body: Enumerator[HttpChunk]): Responder =
      Responder(ResponsePrelude(this, Headers.Empty), Responder.replace(body))

    // Here is our ugly duckling
    def feed[A](body: Enumerator[A] = Enumerator.eof)(implicit w: Writable[A]): Responder =
      feedChunk(body.map(w.toChunk(_)))

    def transform(enumeratee: Enumeratee[HttpChunk, HttpChunk]) =
      Responder(ResponsePrelude(this, Headers.Empty), Enumeratee.passAlong compose enumeratee)
  }

  class RedirectStatusLine(code: Int, reason: String) extends StatusLine(code, reason) {
    def apply(uri: String): Responder = Responder(ResponsePrelude(
      status = this, headers = Headers(HttpHeaders.Location(uri))
    ))
  }

  /**
   * Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
   */
  val Continue = new EmptyBodyStatusLine(100, "Continue")
  val SwitchingProtocols = new EmptyBodyStatusLine(101, "SwitchingProtocols")
  val Processing = new EmptyBodyStatusLine(102, "Processing")
  val Ok = new StatusLineWithBody(200, "OK")
  val Created = new StatusLineWithBody(201, "Created")
  val Accepted = new StatusLineWithBody(202, "Accepted")
  val NonAuthoritativeInformation = new StatusLineWithBody(203, "Non-Authoritative Information")
  val NoContent = new EmptyBodyStatusLine(204, "No Content")
  val ResetContent = new EmptyBodyStatusLine(205, "Reset Content")
  val PartialContent = new StatusLineWithBody(206, "Partial Content")
  val MultiStatus = new StatusLineWithBody(207, "Multi-Status")
  val AlreadyReported = new StatusLineWithBody(208, "Already Reported")
  val IMUsed = new StatusLineWithBody(226, "IM Used")
  val MultipleChoices = new StatusLineWithBody(300, "Multiple Choices")
  val MovedPermanently = new RedirectStatusLine(301, "Moved Permanently")
  val Found = new RedirectStatusLine(302, "Found")
  val SeeOther = new RedirectStatusLine(303, "See Other")
  val NotModified = new EmptyBodyStatusLine(304, "Not Modified")
  val UseProxy = new RedirectStatusLine(305, "Use Proxy")
  val TemporaryRedirect = new RedirectStatusLine(307, "Temporary Redirect")
  val BadRequest = new StatusLineWithBody(400, "Bad Request")
  val Unauthorized = new StatusLineWithBody(401, "Unauthorized")
  val PaymentRequired = new StatusLineWithBody(402, "Payment Required")
  val Forbidden = new StatusLineWithBody(403, "Forbidden")
  val NotFound = new StatusLineWithBody(404, "Not Found")
  val MethodNotAllowed = new StatusLineWithBody(405, "Method Not Allowed")
  val NotAcceptable = new StatusLineWithBody(406, "Not Acceptable")
  val ProxyAuthenticationRequired = new StatusLineWithBody(407, "Proxy Authentication Required")
  val RequestTimeOut = new StatusLineWithBody(408, "Request Time-out")
  val Conflict = new StatusLineWithBody(409, "Conflict")
  val Gone = new StatusLineWithBody(410, "Gone")
  val LengthRequired = new StatusLineWithBody(411, "Length Required")
  val PreconditionFailed = new StatusLineWithBody(412, "Precondition Failed")
  val RequestEntityTooLarge = new StatusLineWithBody(413, "Request Entity Too Large")
  val RequestUriTooLarge = new StatusLineWithBody(414, "Request-URI Too Large")
  val UnsupportedMediaType = new StatusLineWithBody(415, "Unsupported Media Type")
  val RequestedRangeNotSatisfiable = new StatusLineWithBody(416, "Requested Range Not Satisfiable")
  val ExpectationFailed = new StatusLineWithBody(417, "ExpectationFailed")
  val ImATeapot = new StatusLineWithBody(418, "I'm a teapot")
  val UnprocessableEntity = new StatusLineWithBody(422, "Unprocessable Entity")
  val Locked = new StatusLineWithBody(423, "Locked")
  val FailedDependency = new StatusLineWithBody(424, "Failed Dependency")
  val UnorderedCollection = new StatusLineWithBody(425, "Unordered Collection")
  val UpgradeRequired = new StatusLineWithBody(426, "Upgrade Required")
  val PreconditionRequired = new StatusLineWithBody(428, "Precondition Required")
  val TooManyRequests = new StatusLineWithBody(429, "Too Many Requests")
  val RequestHeaderFieldsTooLarge = new StatusLineWithBody(431, "Request Header Fields Too Large")
  val InternalServerError = new StatusLineWithBody(500, "Internal Server Error")
  val NotImplemented = new StatusLineWithBody(501, "Not Implemented")
  val BadGateway = new StatusLineWithBody(502, "Bad Gateway")
  val ServiceUnavailable = new StatusLineWithBody(503, "Service Unavailable")
  val GatewayTimeOut = new StatusLineWithBody(504, "Gateway Time-out")
  val HttpVersionNotSupported = new StatusLineWithBody(505, "HTTP Version not supported")
  val VariantAlsoNegotiates = new StatusLineWithBody(506, "Variant Also Negotiates")
  val InsufficientStorage = new StatusLineWithBody(507, "Insufficient Storage")
  val LoopDetected = new StatusLineWithBody(508, "Loop Detected")
  val NotExtended = new StatusLineWithBody(510, "Not Extended")
  val NetworkAuthenticationRequired = new StatusLineWithBody(511, "Network Authentication Required")

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
