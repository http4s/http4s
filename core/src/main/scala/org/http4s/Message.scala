package org.http4s

import cats._
import cats.implicits._
import fs2._
import fs2.text._
import java.io.File
import java.net.{InetAddress, InetSocketAddress}
import org.http4s.headers._
import org.http4s.server.ServerSoftware
import org.log4s.getLogger

/**
  * Represents a HTTP Message. The interesting subclasses are Request and
  * Response while most of the functionality is found in [[MessageOps]] and
  * [[ResponseOps]]
  * @see [[MessageOps]], [[ResponseOps]]
  */
sealed trait Message[F[_]] extends MessageOps[F] { self =>
  type Self <: Message[F] { type Self = self.Self }

  def httpVersion: HttpVersion

  def headers: Headers

  def body: EntityBody[F]

  final def bodyAsText(implicit defaultCharset: Charset = DefaultCharset): Stream[F, String] =
    charset.getOrElse(defaultCharset) match {
      case Charset.`UTF-8` =>
        // suspect this one is more efficient, though this is superstition
        body.through(utf8Decode)
      case cs =>
        body.through(util.decode(cs))
    }

  def attributes: AttributeMap

  protected def change(
      body: EntityBody[F] = body,
      headers: Headers = headers,
      attributes: AttributeMap = attributes): Self

  override def transformHeaders(f: Headers => Headers)(implicit F: Functor[F]): Self =
    change(headers = f(headers))

  override def withAttribute[A](key: AttributeKey[A], value: A)(implicit F: Functor[F]): Self =
    change(attributes = attributes.put(key, value))

  @deprecated("Use withEntity", "0.19")
  def withBody[T](b: T)(implicit F: Applicative[F], w: EntityEncoder[F, T]): F[Self] =
    F.pure(withEntity(b))

  /** Replace the body of this message with a new body
    *
    * @param b body to attach to this method
    * @param w [[EntityEncoder]] with which to convert the body to an [[EntityBody]]
    * @tparam T type of the Body
    * @return a new message with the new body
    */
  def withEntity[T](b: T)(implicit w: EntityEncoder[F, T]): Self = {
    val entity = w.toEntity(b)
    val hs = entity.length match {
      case Some(l) =>
        `Content-Length`
          .fromLong(l)
          .fold(_ => {
            Message.logger.warn(s"Attempt to provide a negative content length of $l")
            w.headers.toList
          }, cl => cl :: w.headers.toList)
      case None => w.headers
    }
    change(body = entity.body, headers = headers ++ hs)
  }

  /** Sets the entity body without affecting headers such as `Transfer-Encoding`
    * or `Content-Length`. Most use cases are better served by [[withEntity]],
    * which uses an [[EntityEncoder]] to maintain the headers.
    */
  def withBodyStream(body: EntityBody[F]): Self

  /** Set an empty entity body on this message, and remove all payload headers
    * that make no sense with an empty body.
    */
  def withEmptyBody(implicit F: Functor[F]): Self =
    withBodyStream(EmptyBody).transformHeaders(_.removePayloadHeaders)

  def contentLength: Option[Long] = headers.get(`Content-Length`).map(_.length)

  def contentType: Option[`Content-Type`] = headers.get(`Content-Type`)

  /** Returns the charset parameter of the `Content-Type` header, if present. Does
    * not introspect the body for media types that define a charset
    * internally.
    */
  def charset: Option[Charset] = contentType.flatMap(_.charset)

  def isChunked: Boolean =
    headers.get(`Transfer-Encoding`).exists(_.values.contains_(TransferCoding.chunked))

  /**
    * The trailer headers, as specified in Section 3.6.1 of RFC 2616.  The resulting
    * task might not complete unless the entire body has been consumed.
    */
  def trailerHeaders(implicit F: Applicative[F]): F[Headers] =
    attributes.get(Message.Keys.TrailerHeaders[F]).getOrElse(F.pure(Headers.empty))

  /** Decode the [[Message]] to the specified type
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the effect which will generate the `DecodeResult[T]`
    */
  override def attemptAs[T](
      implicit F: FlatMap[F],
      decoder: EntityDecoder[F, T]): DecodeResult[F, T] =
    decoder.decode(this, strict = false)
}

object Message {
  private[http4s] val logger = getLogger
  object Keys {
    private[this] val trailerHeaders: AttributeKey[Any] = AttributeKey[Any]
    def TrailerHeaders[F[_]]: AttributeKey[F[Headers]] =
      trailerHeaders.asInstanceOf[AttributeKey[F[Headers]]]
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
  * @param body fs2.Stream[F, Byte] defining the body of the request
  * @param attributes Immutable Map used for carrying additional information in a type safe fashion
  */
sealed abstract case class Request[F[_]](
    method: Method = Method.GET,
    uri: Uri = Uri(path = "/"),
    httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
    headers: Headers = Headers.empty,
    body: EntityBody[F] = EmptyBody,
    attributes: AttributeMap = AttributeMap.empty
) extends Message[F]
    with RequestOps[F] {
  import Request._

  type Self = Request[F]

  private def requestCopy(
      method: Method = this.method,
      uri: Uri = this.uri,
      httpVersion: HttpVersion = this.httpVersion,
      headers: Headers = this.headers,
      body: EntityBody[F] = this.body,
      attributes: AttributeMap = this.attributes
  ): Request[F] =
    Request(
      method = method,
      uri = uri,
      httpVersion = httpVersion,
      headers = headers,
      body = body,
      attributes = attributes
    )

  @deprecated(
    message = "Copy method is unsafe for setting path info. Use with... methods instead",
    "0.17.0-M3")
  def copy(
      method: Method = this.method,
      uri: Uri = this.uri,
      httpVersion: HttpVersion = this.httpVersion,
      headers: Headers = this.headers,
      body: EntityBody[F] = this.body,
      attributes: AttributeMap = this.attributes
  ): Request[F] =
    requestCopy(
      method = method,
      uri = uri,
      httpVersion = httpVersion,
      headers = headers,
      body = body,
      attributes = attributes
    )

  def withMethod(method: Method) = requestCopy(method = method)
  def withUri(uri: Uri) =
    requestCopy(uri = uri, attributes = attributes -- Request.Keys.PathInfoCaret)
  def withHttpVersion(httpVersion: HttpVersion) = requestCopy(httpVersion = httpVersion)
  def withHeaders(headers: Headers) = requestCopy(headers = headers)
  def withAttributes(attributes: AttributeMap) = requestCopy(attributes = attributes)

  def withBodyStream(body: EntityBody[F]): Request[F] =
    requestCopy(body = body)

  override protected def change(
      body: EntityBody[F],
      headers: Headers,
      attributes: AttributeMap): Self =
    requestCopy(body = body, headers = headers, attributes = attributes)

  lazy val authType: Option[AuthScheme] = headers.get(Authorization).map(_.credentials.authScheme)

  lazy val (scriptName, pathInfo) = {
    val caret = attributes.get(Request.Keys.PathInfoCaret).getOrElse(0)
    uri.path.splitAt(caret)
  }

  def withPathInfo(pi: String)(implicit F: Functor[F]): Self =
    withUri(uri.withPath(scriptName + pi))

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

  /** View of the head elements of the URI parameters in query string.
    *
    * In case a parameter has no value the map returns an empty string.
    *
    * @see multiParams
    */
  def params: Map[String, String] = uri.params

  private lazy val connectionInfo = attributes.get(Keys.ConnectionInfo)

  lazy val remote: Option[InetSocketAddress] = connectionInfo.map(_.remote)

  /**
    Returns the the forwardFor value if present, else the remote address.
    */
  def from: Option[InetAddress] =
    headers
      .get(`X-Forwarded-For`)
      .fold(remote.flatMap(remote => Option(remote.getAddress)))(_.values.head)

  lazy val remoteAddr: Option[String] = remote.map(_.getHostString)
  lazy val remoteHost: Option[String] = remote.map(_.getHostName)
  lazy val remotePort: Option[Int] = remote.map(_.getPort)

  lazy val remoteUser: Option[String] = None

  lazy val server: Option[InetSocketAddress] = connectionInfo.map(_.local)
  lazy val serverAddr: String =
    server
      .map(_.getHostString)
      .orElse(uri.host.map(_.value))
      .orElse(headers.get(Host).map(_.host))
      .getOrElse(InetAddress.getLocalHost.getHostName)

  lazy val serverPort: Int =
    server
      .map(_.getPort)
      .orElse(uri.port)
      .orElse(headers.get(Host).flatMap(_.port))
      .getOrElse(80) // scalastyle:ignore

  /** Whether the Request was received over a secure medium */
  lazy val isSecure: Option[Boolean] = connectionInfo.map(_.secure)

  def serverSoftware: ServerSoftware =
    attributes.get(Keys.ServerSoftware).getOrElse(ServerSoftware.Unknown)

  def decodeWith[A](decoder: EntityDecoder[F, A], strict: Boolean)(f: A => F[Response[F]])(
      implicit F: Monad[F]): F[Response[F]] =
    decoder.decode(this, strict = strict).fold(_.toHttpResponse[F](httpVersion), f).flatten

  override def toString: String =
    s"""Request(method=$method, uri=$uri, headers=${headers.redactSensitive()})"""

}

object Request {

  def apply[F[_]](
      method: Method = Method.GET,
      uri: Uri = Uri(path = "/"),
      httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
      headers: Headers = Headers.empty,
      body: EntityBody[F] = EmptyBody,
      attributes: AttributeMap = AttributeMap.empty
  ): Request[F] =
    new Request[F](
      method = method,
      uri = uri,
      httpVersion = httpVersion,
      headers = headers,
      body = body,
      attributes = attributes
    ) {}

  final case class Connection(local: InetSocketAddress, remote: InetSocketAddress, secure: Boolean)

  object Keys {
    val PathInfoCaret = AttributeKey[Int]
    val PathTranslated = AttributeKey[File]
    val ConnectionInfo = AttributeKey[Connection]
    val ServerSoftware = AttributeKey[ServerSoftware]
  }
}

/** Representation of the HTTP response to send back to the client
  *
  * @param status [[Status]] code and message
  * @param headers [[Headers]] containing all response headers
  * @param body EntityBody[F] representing the possible body of the response
  * @param attributes [[AttributeMap]] containing additional parameters which may be used by the http4s
  *                   backend for additional processing such as java.io.File object
  */
final case class Response[F[_]](
    status: Status = Status.Ok,
    httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
    headers: Headers = Headers.empty,
    body: EntityBody[F] = EmptyBody,
    attributes: AttributeMap = AttributeMap.empty)
    extends Message[F]
    with ResponseOps[F] {
  type Self = Response[F]

  override def withStatus(status: Status)(implicit F: Functor[F]): Self =
    copy(status = status)

  def withBodyStream(body: EntityBody[F]): Self =
    copy(body = body)

  override protected def change(
      body: EntityBody[F],
      headers: Headers,
      attributes: AttributeMap): Self =
    copy(body = body, headers = headers, attributes = attributes)

  override def toString: String = {
    val newHeaders = headers.redactSensitive()
    s"""Response(status=${status.code}, headers=$newHeaders)"""
  }

  /** Returns a list of cookies from the [[org.http4s.headers.Set-Cookie]]
    * headers. Includes expired cookies, such as those that represent cookie
    * deletion. */
  def cookies: List[ResponseCookie] =
    `Set-Cookie`.from(headers).map(_.cookie)
}

object Response {
  private[this] val pureNotFound: Response[Pure] =
    Response(
      Status.NotFound,
      body = Stream("Not found").through(text.utf8Encode),
      headers = Headers(`Content-Type`(MediaType.`text/plain`, Charset.`UTF-8`)))

  def notFound[F[_]]: Response[F] = pureNotFound.copy(body = pureNotFound.body.covary[F])

  def notFoundFor[F[_]: Monad](request: Request[F])(
      implicit encoder: EntityEncoder[F, String]): F[Response[F]] =
    Monad[F].pure(Response(Status.NotFound).withEntity(s"${request.pathInfo} not found"))
}
