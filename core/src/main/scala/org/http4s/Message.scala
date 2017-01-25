package org.http4s

import java.io.File
import java.net.{InetSocketAddress, InetAddress}

import cats._
import fs2._
import fs2.text._
import org.http4s.headers._
import org.http4s.batteries._
import org.http4s.server.ServerSoftware

/**
 * Represents a HTTP Message. The interesting subclasses are Request and Response
 * while most of the functionality is found in [[MessageSyntax]] and [[ResponseOps]]
 * @see [[MessageSyntax]], [[ResponseOps]]
 */
sealed trait Message extends MessageOps { self =>
  type Self <: Message { type Self = self.Self }

  def httpVersion: HttpVersion

  def headers: Headers

  def body: EntityBody

  final def bodyAsText(implicit defaultCharset: Charset = DefaultCharset): Stream[Task, String] = {
    (charset getOrElse defaultCharset) match {
      case Charset.`UTF-8` =>
        // suspect this one is more efficient, though this is superstition
        body.through(utf8Decode)
      case cs =>
        body.through(util.decode(cs))
    }
  }

  /** True if and only if the body is composed solely of Emits and Halt.  This
    * indicates that the body can be re-run without side-effects. */
  // TODO fs2 port need to replace unemit
  /*
  def isBodyPure: Boolean =
    body.unemit._2.isHalt
   */
  
  def attributes: AttributeMap

  protected def change(body: EntityBody = body,
                       headers: Headers = headers,
                       attributes: AttributeMap = attributes): Self

  override def transformHeaders(f: Headers => Headers): Self =
    change(headers = f(headers))

  override def withAttribute[A](key: AttributeKey[A], value: A): Self =
    change(attributes = attributes.put(key, value))

  /** Replace the body of this message with a new body
    *
    * @param b body to attach to this method
    * @param w [[EntityEncoder]] with which to convert the body to an [[EntityBody]]
    * @tparam T type of the Body
    * @return a new message with the new body
    */
  def withBody[T](b: T)(implicit w: EntityEncoder[T]): Task[Self] = {
    w.toEntity(b).map { entity =>
      val hs = entity.length match {
        case Some(l) => `Content-Length`(l)::w.headers.toList
        case None    => w.headers
      }
      change(body = entity.body, headers = headers ++ hs)
    }
  }

  def contentLength: Option[Long] = headers.get(`Content-Length`).map(_.length)

  def contentType: Option[`Content-Type`] = headers.get(`Content-Type`)

  /** Returns the charset parameter of the `Content-Type` header, if present.
    * Does not introspect the body for media types that define a charset internally. */
  def charset: Option[Charset] = contentType.flatMap(_.charset)

  def isChunked: Boolean = headers.get(`Transfer-Encoding`).exists(_.values.contains(TransferCoding.chunked))

  /**
   * The trailer headers, as specified in Section 3.6.1 of RFC 2616.  The resulting
   * task might not complete unless the entire body has been consumed.
   */
  def trailerHeaders: Task[Headers] = attributes.get(Message.Keys.TrailerHeaders).getOrElse(Task.now(Headers.empty))

  /** Decode the [[Message]] to the specified type
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the `Task` which will generate the `DecodeResult[T]`
    */
  override def attemptAs[T](implicit decoder: EntityDecoder[T]): DecodeResult[T] =
    decoder.decode(this, strict = false)
}

object Message {
  object Keys {
    val TrailerHeaders = AttributeKey.http4s[Task[Headers]]("trailer-headers")
  }
}

/** Representation of an incoming HTTP message
  *
  * A Request encapsulates the entirety of the incoming HTTP request including the
  * status line, headers, and a possible request body.
  *
  * @param method [[Method.GET]], [[Method.POST]], etc.
  * @param uri representation of the request URI
  * @param httpVersion the HTTP version
  * @param headers collection of [[Header]]s
  * @param body scalaz.stream.Process[Task,Chunk] defining the body of the request
  * @param attributes Immutable Map used for carrying additional information in a type safe fashion
  */
final case class Request(
  method: Method = Method.GET,
  uri: Uri = Uri(path = "/"),
  httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
  headers: Headers = Headers.empty,
  body: EntityBody = EmptyBody,
  attributes: AttributeMap = AttributeMap.empty
) extends Message with RequestOps {
  import Request._

  type Self = Request

  override protected def change(body: EntityBody, headers: Headers, attributes: AttributeMap): Self =
    copy(body = body, headers = headers, attributes = attributes)

  lazy val authType: Option[AuthScheme] = headers.get(Authorization).map(_.credentials.authScheme)

  lazy val (scriptName, pathInfo) = {
    val caret = attributes.get(Request.Keys.PathInfoCaret).getOrElse(0)
    uri.path.splitAt(caret)
  }

  def withPathInfo(pi: String): Request =
    copy(uri = uri.withPath(scriptName + pi))

  lazy val pathTranslated: Option[File] = attributes.get(Keys.PathTranslated)

  def queryString: String = uri.query.renderString

  /**
   * Representation of the query string as a map
   *
   * In case a parameter is available in query string but no value is there the
   * sequence will be empty. If the value is empty the the sequence contains an
   * empty string.
   *
   * =====Examples=====
   * <table>
   * <tr><th>Query String</th><th>Map</th></tr>
   * <tr><td><code>?param=v</code></td><td><code>Map("param" -> Seq("v"))</code></td></tr>
   * <tr><td><code>?param=</code></td><td><code>Map("param" -> Seq(""))</code></td></tr>
   * <tr><td><code>?param</code></td><td><code>Map("param" -> Seq())</code></td></tr>
   * <tr><td><code>?=value</code></td><td><code>Map("" -> Seq("value"))</code></td></tr>
   * <tr><td><code>?p1=v1&amp;p1=v2&amp;p2=v3&amp;p2=v3</code></td><td><code>Map("p1" -> Seq("v1","v2"), "p2" -> Seq("v3","v4"))</code></td></tr>
   * </table>
   *
   * The query string is lazily parsed. If an error occurs during parsing
   * an empty `Map` is returned.
   */
  def multiParams: Map[String, Seq[String]] = uri.multiParams

  /**
   * View of the head elements of the URI parameters in query string.
   *
   * In case a parameter has no value the map returns an empty string.
   *
   * @see multiParams
   */
  def params: Map[String, String] = uri.params

  private lazy val connectionInfo = attributes.get(Keys.ConnectionInfo)

  lazy val remote: Option[InetSocketAddress] = connectionInfo.map(_.remote)
  lazy val remoteAddr: Option[String] = remote.map(_.getHostString)
  lazy val remoteHost: Option[String] = remote.map(_.getHostName)
  lazy val remotePort: Option[Int]    = remote.map(_.getPort)

  lazy val remoteUser: Option[String] = None

  lazy val server: Option[InetSocketAddress] = connectionInfo.map(_.local)
  lazy val serverAddr: String = {
    server.map(_.getHostString)
      .orElse(uri.host.map(_.value))
      .orElse(headers.get(Host).map(_.host))
      .getOrElse(InetAddress.getLocalHost.getHostName)
  }

  lazy val serverPort: Int = {
    server.map(_.getPort)
      .orElse(uri.port)
      .orElse(headers.get(Host).flatMap(_.port))
      .getOrElse(80) // scalastyle:ignore
  }

  /** Whether the Request was received over a secure medium */
  lazy val isSecure: Option[Boolean] = connectionInfo.map(_.secure)

  def serverSoftware: ServerSoftware = attributes.get(Keys.ServerSoftware).getOrElse(ServerSoftware.Unknown)

  def decodeWith[A](decoder: EntityDecoder[A], strict: Boolean)(f: A => Task[Response]): Task[Response] =
    decoder.decode(this, strict = strict).fold(_.toHttpResponse(httpVersion), f).flatten

  override def toString: String =
    s"""Request(method=$method, uri=$uri, headers=${headers}"""

  /** A request is idempotent if and only if its method is idempotent and its body
    * is pure.  If true, this request can be submitted multipe times. */
  // TODO fs2 port uncomment when isBodyPure is back
  /*
  def isIdempotent: Boolean =
    method.isIdempotent && isBodyPure
   */
}

object Request {

  final case class Connection(local: InetSocketAddress, remote: InetSocketAddress, secure: Boolean)

  object Keys {
    val PathInfoCaret = AttributeKey.http4s[Int]("request.pathInfoCaret")
    val PathTranslated = AttributeKey.http4s[File]("request.pathTranslated")
    val ConnectionInfo = AttributeKey.http4s[Connection]("request.remote")
    val ServerSoftware = AttributeKey.http4s[ServerSoftware]("request.serverSoftware")
  }
}

/**
 * Represents that a service either returns a [[Response]] or a [[Pass]] to fall through
 * to another service.
 */
sealed trait MaybeResponse {
  def cata[A](f: Response => A, a: => A): A =
    this match {
      case r: Response => f(r)
      case Pass => a
    }

  def orElse[B >: Response](b: => B): B =
    this match {
      case r: Response => r
      case Pass => b
    }

  def orNotFound: Response =
    orElse(Response(Status.NotFound))

  def toOption: Option[Response] =
    cata(Some(_), None)
}

object MaybeResponse {
  implicit val instance: Monoid[MaybeResponse] =
    new Monoid[MaybeResponse] {
      def empty =
        Pass
      def combine(a: MaybeResponse, b: MaybeResponse) =
        a orElse b
    }

  implicit val taskInstance: Monoid[Task[MaybeResponse]] =
    new Monoid[Task[MaybeResponse]] {
      def empty =
        Pass.now
      def combine(ta: Task[MaybeResponse], tb: Task[MaybeResponse]): Task[MaybeResponse] =
        ta.flatMap(_.cata(Task.now, tb))
    }
}

case object Pass extends MaybeResponse {
  val now: Task[MaybeResponse] =
    Task.now(Pass)
}

/** Representation of the HTTP response to send back to the client
 *
 * @param status [[Status]] code and message
 * @param headers [[Headers]] containing all response headers
 * @param body scalaz.stream.Process[Task,Chunk] representing the possible body of the response
 * @param attributes [[AttributeMap]] containing additional parameters which may be used by the http4s
 *                   backend for additional processing such as java.io.File object
 */
final case class Response(
  status: Status = Status.Ok,
  httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
  headers: Headers = Headers.empty,
  body: EntityBody = EmptyBody,
  attributes: AttributeMap = AttributeMap.empty)
    extends Message with MaybeResponse with ResponseOps {
  type Self = Response

  override def withStatus(status: Status): Self =
    copy(status = status)

  override protected def change(body: EntityBody, headers: Headers, attributes: AttributeMap): Self =
    copy(body = body, headers = headers, attributes = attributes)

  override def toString: String =
    s"""Response(status=${status.code}, headers=$headers)"""
}

object Response {
  @deprecated("Use Pass.now instead", "0.16")
  val fallthrough: Task[MaybeResponse] =
    Pass.now

  def notFound(request: Request): Task[Response] = {
    val body = s"${request.pathInfo} not found"
    Response(Status.NotFound).withBody(body)
  }
}
