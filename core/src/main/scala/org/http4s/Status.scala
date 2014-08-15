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

object Status extends StatusConstants {
  /** Helper for the generation of a [[org.http4s.Response]] which will not contain a body
    *
    * While it is possible to for the [[org.http4s.Response]] manually, the EntityResponseGenerators
    * offer shortcut syntax to make intention clear and concise.
    *
    * @example {{{
    * val resp: Task[Response] = Status.Continue()
    * }}}
    *
    * @see [[EntityResponse]]
    */
  trait NoEntityResponse// { self: Status =>
//    private[this] val StatusResponder = Response(this)
//    def apply(): Task[Response] = Task.now(StatusResponder)
//  }

  /** Helper for the generation of a [[org.http4s.Response]] which may contain a body
    *
    * While it is possible to for the [[org.http4s.Response]] manually, the EntityResponseGenerators
    * offer shortcut syntax to make intention clear and concise.
    *
    * @example {{{
    * val resp: Task[Response] = Ok("Hello world!")
    * }}}
    *
    * @see [[NoEntityResponse]]
    */
  trait EntityResponse //{ self: Status =>
//    private[this] val StatusResponder = Response(this)
//
//    def apply(): Task[Response] = Task.now(StatusResponder)
//
//    def apply[A](body: A)(implicit w: Writable[A]): Task[Response] =
//      apply(body, w.headers)(w)
//
//    def apply[A](body: A, headers: Headers)(implicit w: Writable[A]): Task[Response] = {
//      var h = headers ++ w.headers
//      w.toEntity(body).flatMap { case Entity(proc, len) =>
//        len foreach { h +:= Header.`Content-Length`(_) }
//        Task.now(Response(status = self, headers = h, body = proc))
//      }
//    }
//  }

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
    * @see [[NoEntityResponse]]
    */
  trait RedirectResponder //{ self: Status =>
//    def apply(uri: String): Response = Response(status = self, headers = Headers(Header.Location(uri)))
//
//    def apply(uri: URI): Response = apply(uri.toString)
//
//    def apply(url: URL): Response = apply(url.toString)
//  }

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

trait StatusConstants {
  import Status.{NoEntityResponse, EntityResponse, RedirectResponder}

  /**
   * Status code list taken from http://www.iana.org/assignments/http-status-codes/http-status-codes.xml
   */
  val Continue = new Status(100, "Continue") with NoEntityResponse
  val SwitchingProtocols = new Status(101, "Switching Protocols") //{
//    // TODO type this header
//    def apply(protocols: String, headers: Headers = Headers.empty): Response =
//      Response(status = this, headers = Header("Upgrade", protocols) +: headers, body = EmptyBody)
//  }
  val Processing = new Status(102, "Processing") with NoEntityResponse

  val Ok = new Status(200, "OK") with EntityResponse
  val Created = new Status(201, "Created") with EntityResponse
  val Accepted = new Status(202, "Accepted") with EntityResponse
  val NonAuthoritativeInformation = new Status(203, "Non-Authoritative Information") with EntityResponse
  val NoContent = new Status(204, "No Content") with NoEntityResponse
  val ResetContent = new Status(205, "Reset Content") with NoEntityResponse
  val PartialContent = new Status(206, "Partial Content") with EntityResponse //{
//    // TODO type this header
//    def apply[A](range: String, body: A, headers: Headers = Headers.empty)(implicit w: Writable[A]): Task[Response] =
//      apply(body).map { r =>
//        headers.foldLeft(r.addHeader(Header("Range", range))) { _.addHeader(_) }
//      }
//  }
  val MultiStatus = new Status(207, "Multi-Status") with EntityResponse
  val AlreadyReported = new Status(208, "Already Reported") with EntityResponse
  val IMUsed = new Status(226, "IM Used") with EntityResponse

  val MultipleChoices = new Status(300, "Multiple Choices") with EntityResponse
  val MovedPermanently = new Status(301, "Moved Permanently") with RedirectResponder
  val Found = new Status(302, "Found") with RedirectResponder
  val SeeOther = new Status(303, "See Other") with RedirectResponder

  val NotModified = new Status(304, "Not Modified") with NoEntityResponse //{
//    override def apply(): Task[Response] = Task.now(Response(status = this, headers = Headers(Header.Date(DateTime.now))))
//  }

  val UseProxy = new Status(305, "Use Proxy") with RedirectResponder
  val TemporaryRedirect = new Status(306, "Temporary Redirect") with RedirectResponder

  val BadRequest = new Status(400, "Bad Request") with EntityResponse
  val Unauthorized = new Status(401, "Unauthorized") with EntityResponse //{
//    def apply[A](wwwAuthenticate: String, body: A, headers: Headers = Headers.empty)(implicit w: Writable[A]): Task[Response] =
//    // TODO type this header
//      apply(body).map { r =>
//        headers.foldLeft(r.addHeader(Header("WWW-Authenticate", wwwAuthenticate))) { _.addHeader(_) }
//      }
//  }
  val PaymentRequired = new Status(402, "Payment Required") with EntityResponse
  val Forbidden = new Status(403, "Forbidden") with EntityResponse
  val NotFound = new Status(404, "Not Found") with EntityResponse //{
//    def apply(request: Request): Task[Response] = apply(s"${request.pathInfo} not found")
//  }
  val MethodNotAllowed = new Status(405, "Method Not Allowed") with EntityResponse //{
//    def apply[A](allowed: TraversableOnce[Method], body: A, headers: Headers = Headers.empty)(implicit w: Writable[A]): Task[Response] =
//      apply(body).map { r =>
//        headers.foldLeft(r.addHeader(Header("Allowed", allowed.mkString(", ")))) { _.addHeader(_) }
//      }
//  }
  val NotAcceptable = new Status(406, "Not Acceptable") with EntityResponse
  val ProxyAuthenticationRequired = new Status(407, "Proxy Authentication Required") with EntityResponse //{
//    // TODO type this header
//    def apply[F[_], A](proxyAuthenticate: String, body: A, headers: Headers = Headers.empty)(implicit w: Writable[A]): Task[Response] =
//      apply(body).map { r =>
//        headers.foldLeft(r.addHeader(Header("Proxy-Authenticate", proxyAuthenticate))) { _.addHeader(_) }
//      }
//  }
  val RequestTimeOut = new Status(408, "Request Time-out") with EntityResponse
  val Conflict = new Status(409, "Conflict") with EntityResponse
  val Gone = new Status(410, "Gone") with EntityResponse
  val LengthRequred = new Status(411, "Length Required") with EntityResponse
  val PreconditionFailed = new Status(412, "Precondition Failed") with EntityResponse
  val RequestEntityTooLarge = new Status(413, "Request Entity Too Large") with EntityResponse
  val RequestUriTooLarge = new Status(414, "Request-URI Too Large") with EntityResponse
  val UnsupportedMediaType = new Status(415, "Unsupported Media Type") with EntityResponse
  val RequestedRangeNotSatisfiable = new Status(416, "Requested Range Not Satisfiable") with EntityResponse
  val ExpectationFailed = new Status(417, "ExpectationFailed") with EntityResponse
  val ImATeapot = new Status(418, "I'm a teapot") with EntityResponse
  val UnprocessableEntity = new Status(422, "Unprocessable Entity") with EntityResponse
  val Locked = new Status(423, "Locked") with EntityResponse
  val FailedDependency = new Status(424, "Failed Dependency") with EntityResponse
  val UnorderedCollection = new Status(425, "Unordered Collection") with EntityResponse
  val UpgradeRequired = new Status(426, "Upgrade Required") with EntityResponse
  val PreconditionRequired = new Status(428, "Precondition Required") with EntityResponse
  val TooManyRequests = new Status(429, "Too Many Requests") with EntityResponse
  val RequestHeaderFieldsTooLarge = new Status(431, "Request Header Fields Too Large") with EntityResponse

  val InternalServerError = new Status(500, "Internal Server Error") with EntityResponse //{
//    /*
//        // TODO Bad in production.  Development mode?  Implicit renderer?
//        def apply(t: Throwable): Response = apply(s"${t.getMessage}\n\nStacktrace:\n${t.getStackTraceString}")
//    */
//  }
  val NotImplemented = new Status(501, "Not Implemented") with EntityResponse
  val BadGateway = new Status(502, "Bad Gateway") with EntityResponse
  val ServiceUnavailable = new Status(503, "Service Unavailable") with EntityResponse
  val GatewayTimeOut = new Status(504, "Gateway Time-out") with EntityResponse
  val HttpVersionNotSupported = new Status(505, "HTTP Version not supported") with EntityResponse
  val VariantAlsoNegotiates = new Status(506, "Variant Also Negotiates") with EntityResponse
  val InsufficientStorage = new Status(507, "Insufficient Storage") with EntityResponse
  val LoopDetected = new Status(508, "Loop Detected") with EntityResponse
  val NotExtended = new Status(510, "Not Extended") with EntityResponse
  val NetworkAuthenticationRequired = new Status(511, "Network Authentication Required") with EntityResponse
}
