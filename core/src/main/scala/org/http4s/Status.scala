package org.http4s

import org.http4s.Writable.Entity

import scalaz.concurrent.Task
import java.net.{URL, URI}
import org.http4s.Header.`Content-Type`

/** Representation of the HTTP response code and reason
  *
  * @param code HTTP status code
  * @param reason reason for the response. eg, OK
  */
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

object Status extends StatusInstances {

  /** Helper for the generation of a [[org.http4s.Response]] which will not contain a body
    *
    * While it is possible to for the [[org.http4s.Response]] manually, the EntityResponseGenerators
    * offer shortcut syntax to make intention clear and concise.
    *
    * @example {{{
    * val resp: Task[Response] = Status.Continue()
    * }}}
    *
    * @see [[EntityResponseGenerator]]
    */
  trait NoEntityResponseGenerator { self: Status =>
    private[this] val StatusResponder = Response(this)
    def apply(): Task[Response] = Task.now(StatusResponder)
  }

  /** Helper for the generation of a [[org.http4s.Response]] which may contain a body
    *
    * While it is possible to for the [[org.http4s.Response]] manually, the EntityResponseGenerators
    * offer shortcut syntax to make intention clear and concise.
    *
    * @example {{{
    * val resp: Task[Response] = Ok("Hello world!")
    * }}}
    *
    * @see [[NoEntityResponseGenerator]]
    */
  trait EntityResponseGenerator { self: Status =>
    private[this] val StatusResponder = Response(this)

    def apply(): Task[Response] = Task.now(StatusResponder)

    def apply[A](body: A)(implicit w: Writable[A]): Task[Response] =
      apply(body, w.headers)(w)

    def apply[A](body: A, headers: Headers)(implicit w: Writable[A]): Task[Response] = {
      var h = headers ++ w.headers
      w.toEntity(body).flatMap { case Entity(proc, len) =>
        len foreach { h +:= Header.`Content-Length`(_) }
        Task.now(Response(status = self, headers = h, body = proc))
      }
    }
  }

  /** Helper for the generation of a [[org.http4s.Response]] which points to another HTTP location
    *
    * The RedirectResponseGenerator aids in adding the appropriate headers for Redirect actions.
    * While it is possible to for the [[org.http4s.Response]] manually, the EntityResponseGenerators
    * offer shortcut syntax to make intention clear and concise.
    *
    * @example {{{
    * val resp: Task[Response] = MovedPermanently("http://foo.com")
    * }}}
    *
    * @see [[NoEntityResponseGenerator]]
    */
  trait RedirectResponderGenerator { self: Status =>
    def apply(uri: String): Response = Response(status = self, headers = Headers(Header.Location(uri)))

    def apply(uri: URI): Response = apply(uri.toString)

    def apply(url: URL): Response = apply(url.toString)
  }

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

trait StatusInstances {
  import Status.{NoEntityResponseGenerator, EntityResponseGenerator, RedirectResponderGenerator}

  /**
   * Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
   */
  val Continue = new Status(100, "Continue") with NoEntityResponseGenerator
  object SwitchingProtocols extends Status(101, "Switching Protocols") {
    // TODO type this header
    def apply(protocols: String, headers: Headers = Headers.empty): Response =
      Response(status = this, headers = Header("Upgrade", protocols) +: headers, body = EmptyBody)
  }
  val Processing = new Status(102, "Processing") with NoEntityResponseGenerator

  val Ok = new Status(200, "OK") with EntityResponseGenerator
  val Created = new Status(201, "Created") with EntityResponseGenerator
  val Accepted = new Status(202, "Accepted") with EntityResponseGenerator
  val NonAuthoritativeInformation = new Status(203, "Non-Authoritative Information") with EntityResponseGenerator
  val NoContent = new Status(204, "No Content") with NoEntityResponseGenerator
  val ResetContent = new Status(205, "Reset Content") with NoEntityResponseGenerator
  object PartialContent extends Status(206, "Partial Content") with EntityResponseGenerator {
    // TODO type this header
    def apply[A](range: String, body: A, headers: Headers = Headers.empty)(implicit w: Writable[A]): Task[Response] =
      apply(body).map { r =>
        headers.foldLeft(r.addHeader(Header("Range", range))) { _.addHeader(_) }
      }
  }
  val MultiStatus = new Status(207, "Multi-Status") with EntityResponseGenerator
  val AlreadyReported = new Status(208, "Already Reported") with EntityResponseGenerator
  val IMUsed = new Status(226, "IM Used") with EntityResponseGenerator

  val MultipleChoices = new Status(300, "Multiple Choices") with EntityResponseGenerator
  val MovedPermanently = new Status(301, "Moved Permanently") with RedirectResponderGenerator
  val Found = new Status(302, "Found") with RedirectResponderGenerator
  val SeeOther = new Status(303, "See Other") with RedirectResponderGenerator

  val NotModified = new Status(304, "Not Modified") with NoEntityResponseGenerator {
    override def apply(): Task[Response] = Task.now(Response(status = this, headers = Headers(Header.Date(DateTime.now))))
  }

  val UseProxy = new Status(305, "Use Proxy") with RedirectResponderGenerator
  val TemporaryRedirect = new Status(306, "Temporary Redirect") with RedirectResponderGenerator

  val BadRequest = new Status(400, "Bad Request") with EntityResponseGenerator
  object Unauthorized extends Status(401, "Unauthorized") with EntityResponseGenerator {
    def apply[A](wwwAuthenticate: String, body: A, headers: Headers = Headers.empty)(implicit w: Writable[A]): Task[Response] =
    // TODO type this header
      apply(body).map { r =>
        headers.foldLeft(r.addHeader(Header("WWW-Authenticate", wwwAuthenticate))) { _.addHeader(_) }
      }
  }
  val PaymentRequired = new Status(402, "Payment Required") with EntityResponseGenerator
  val Forbidden = new Status(403, "Forbidden") with EntityResponseGenerator
  object NotFound extends Status(404, "Not Found") with EntityResponseGenerator {
    def apply(request: Request): Task[Response] = apply(s"${request.pathInfo} not found")
  }
  object MethodNotAllowed extends Status(405, "Method Not Allowed") with EntityResponseGenerator {
    def apply[A](allowed: TraversableOnce[Method], body: A, headers: Headers = Headers.empty)(implicit w: Writable[A]): Task[Response] =
      apply(body).map { r =>
        headers.foldLeft(r.addHeader(Header("Allowed", allowed.mkString(", ")))) { _.addHeader(_) }
      }
  }
  val NotAcceptable = new Status(406, "Not Acceptable") with EntityResponseGenerator
  object ProxyAuthenticationRequired extends Status(407, "Proxy Authentication Required") with EntityResponseGenerator {
    // TODO type this header
    def apply[F[_], A](proxyAuthenticate: String, body: A, headers: Headers = Headers.empty)(implicit w: Writable[A]): Task[Response] =
      apply(body).map { r =>
        headers.foldLeft(r.addHeader(Header("Proxy-Authenticate", proxyAuthenticate))) { _.addHeader(_) }
      }
  }
  val RequestTimeOut = new Status(408, "Request Time-out") with EntityResponseGenerator
  val Conflict = new Status(409, "Conflict") with EntityResponseGenerator
  val Gone = new Status(410, "Gone") with EntityResponseGenerator
  val LengthRequred = new Status(411, "Length Required") with EntityResponseGenerator
  val PreconditionFailed = new Status(412, "Precondition Failed") with EntityResponseGenerator
  val RequestEntityTooLarge = new Status(413, "Request Entity Too Large") with EntityResponseGenerator
  val RequestUriTooLarge = new Status(414, "Request-URI Too Large") with EntityResponseGenerator
  val UnsupportedMediaType = new Status(415, "Unsupported Media Type") with EntityResponseGenerator
  val RequestedRangeNotSatisfiable = new Status(416, "Requested Range Not Satisfiable") with EntityResponseGenerator
  val ExpectationFailed = new Status(417, "ExpectationFailed") with EntityResponseGenerator
  val ImATeapot = new Status(418, "I'm a teapot") with EntityResponseGenerator
  val UnprocessableEntity = new Status(422, "Unprocessable Entity") with EntityResponseGenerator
  val Locked = new Status(423, "Locked") with EntityResponseGenerator
  val FailedDependency = new Status(424, "Failed Dependency") with EntityResponseGenerator
  val UnorderedCollection = new Status(425, "Unordered Collection") with EntityResponseGenerator
  val UpgradeRequired = new Status(426, "Upgrade Required") with EntityResponseGenerator
  val PreconditionRequired = new Status(428, "Precondition Required") with EntityResponseGenerator
  val TooManyRequests = new Status(429, "Too Many Requests") with EntityResponseGenerator
  val RequestHeaderFieldsTooLarge = new Status(431, "Request Header Fields Too Large") with EntityResponseGenerator

  val InternalServerError = new Status(500, "Internal Server Error") with EntityResponseGenerator {
    /*
        // TODO Bad in production.  Development mode?  Implicit renderer?
        def apply(t: Throwable): Response = apply(s"${t.getMessage}\n\nStacktrace:\n${t.getStackTraceString}")
    */
  }
  val NotImplemented = new Status(501, "Not Implemented") with EntityResponseGenerator
  val BadGateway = new Status(502, "Bad Gateway") with EntityResponseGenerator
  val ServiceUnavailable = new Status(503, "Service Unavailable") with EntityResponseGenerator
  val GatewayTimeOut = new Status(504, "Gateway Time-out") with EntityResponseGenerator
  val HttpVersionNotSupported = new Status(505, "HTTP Version not supported") with EntityResponseGenerator
  val VariantAlsoNegotiates = new Status(506, "Variant Also Negotiates") with EntityResponseGenerator
  val InsufficientStorage = new Status(507, "Insufficient Storage") with EntityResponseGenerator
  val LoopDetected = new Status(508, "Loop Detected") with EntityResponseGenerator
  val NotExtended = new Status(510, "Not Extended") with EntityResponseGenerator
  val NetworkAuthenticationRequired = new Status(511, "Network Authentication Required") with EntityResponseGenerator
}
