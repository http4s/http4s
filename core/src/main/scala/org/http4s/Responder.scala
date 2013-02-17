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
  class EmptyBodyStatus(code: Int, reason: String) extends Status(code, reason) {
    def apply(): Responder = Responder(ResponsePrelude(this, Headers.Empty), Responder.EmptyBody)
  }

  class StatusWithBody(code: Int, reason: String) extends EmptyBodyStatus(code, reason) {
    def apply[A](body: A)(implicit w: Writable[A]): Responder = feedChunk(Enumerator(w.toChunk(body)))

    def feedChunk(body: Enumerator[HttpChunk]): Responder =
      Responder(ResponsePrelude(this, Headers.Empty), Responder.replace(body))

    // Here is our ugly duckling
    def feed[A](body: Enumerator[A] = Enumerator.eof)(implicit w: Writable[A]): Responder =
      feedChunk(body.map(w.toChunk(_)))

    def transform(enumeratee: Enumeratee[HttpChunk, HttpChunk]) =
      Responder(ResponsePrelude(this, Headers.Empty), Enumeratee.passAlong compose enumeratee)
  }

  class RedirectStatus(code: Int, reason: String) extends Status(code, reason) {
    def apply(uri: String): Responder = Responder(ResponsePrelude(
      status = this, headers = Headers(HttpHeaders.Location(uri))
    ))
  }

  /**
   * Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
   */
  val Continue = new EmptyBodyStatus(100, "Continue")
  val SwitchingProtocols = new EmptyBodyStatus(101, "SwitchingProtocols")
  val Processing = new EmptyBodyStatus(102, "Processing")
  val Ok = new StatusWithBody(200, "OK")
  val Created = new StatusWithBody(201, "Created")
  val Accepted = new StatusWithBody(202, "Accepted")
  val NonAuthoritativeInformation = new StatusWithBody(203, "Non-Authoritative Information")
  val NoContent = new EmptyBodyStatus(204, "No Content")
  val ResetContent = new EmptyBodyStatus(205, "Reset Content")
  val PartialContent = new StatusWithBody(206, "Partial Content")
  val MultiStatus = new StatusWithBody(207, "Multi-Status")
  val AlreadyReported = new StatusWithBody(208, "Already Reported")
  val IMUsed = new StatusWithBody(226, "IM Used")
  val MultipleChoices = new StatusWithBody(300, "Multiple Choices")
  val MovedPermanently = new RedirectStatus(301, "Moved Permanently")
  val Found = new RedirectStatus(302, "Found")
  val SeeOther = new RedirectStatus(303, "See Other")
  val NotModified = new EmptyBodyStatus(304, "Not Modified")
  val UseProxy = new RedirectStatus(305, "Use Proxy")
  val TemporaryRedirect = new RedirectStatus(307, "Temporary Redirect")
  val BadRequest = new StatusWithBody(400, "Bad Request")
  val Unauthorized = new StatusWithBody(401, "Unauthorized")
  val PaymentRequired = new StatusWithBody(402, "Payment Required")
  val Forbidden = new StatusWithBody(403, "Forbidden")
  val NotFound = new StatusWithBody(404, "Not Found")
  val MethodNotAllowed = new StatusWithBody(405, "Method Not Allowed")
  val NotAcceptable = new StatusWithBody(406, "Not Acceptable")
  val ProxyAuthenticationRequired = new StatusWithBody(407, "Proxy Authentication Required")
  val RequestTimeOut = new StatusWithBody(408, "Request Time-out")
  val Conflict = new StatusWithBody(409, "Conflict")
  val Gone = new StatusWithBody(410, "Gone")
  val LengthRequired = new StatusWithBody(411, "Length Required")
  val PreconditionFailed = new StatusWithBody(412, "Precondition Failed")
  val RequestEntityTooLarge = new StatusWithBody(413, "Request Entity Too Large")
  val RequestUriTooLarge = new StatusWithBody(414, "Request-URI Too Large")
  val UnsupportedMediaType = new StatusWithBody(415, "Unsupported Media Type")
  val RequestedRangeNotSatisfiable = new StatusWithBody(416, "Requested Range Not Satisfiable")
  val ExpectationFailed = new StatusWithBody(417, "ExpectationFailed")
  val ImATeapot = new StatusWithBody(418, "I'm a teapot")
  val UnprocessableEntity = new StatusWithBody(422, "Unprocessable Entity")
  val Locked = new StatusWithBody(423, "Locked")
  val FailedDependency = new StatusWithBody(424, "Failed Dependency")
  val UnorderedCollection = new StatusWithBody(425, "Unordered Collection")
  val UpgradeRequired = new StatusWithBody(426, "Upgrade Required")
  val PreconditionRequired = new StatusWithBody(428, "Precondition Required")
  val TooManyRequests = new StatusWithBody(429, "Too Many Requests")
  val RequestHeaderFieldsTooLarge = new StatusWithBody(431, "Request Header Fields Too Large")
  val InternalServerError = new StatusWithBody(500, "Internal Server Error")
  val NotImplemented = new StatusWithBody(501, "Not Implemented")
  val BadGateway = new StatusWithBody(502, "Bad Gateway")
  val ServiceUnavailable = new StatusWithBody(503, "Service Unavailable")
  val GatewayTimeOut = new StatusWithBody(504, "Gateway Time-out")
  val HttpVersionNotSupported = new StatusWithBody(505, "HTTP Version not supported")
  val VariantAlsoNegotiates = new StatusWithBody(506, "Variant Also Negotiates")
  val InsufficientStorage = new StatusWithBody(507, "Insufficient Storage")
  val LoopDetected = new StatusWithBody(508, "Loop Detected")
  val NotExtended = new StatusWithBody(510, "Not Extended")
  val NetworkAuthenticationRequired = new StatusWithBody(511, "Network Authentication Required")

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

object ResponderGenerators {
  import Status._

  def genRouteErrorResponse(t: Throwable): Responder = {
    InternalServerError(s"${t.getMessage}\n\nStacktrace:\n${t.getStackTraceString}")
  }

  def genRouteNotFound(request: RequestPrelude): Responder = {
    NotFound(s"${request.pathInfo} Not Found.")
  }
}
