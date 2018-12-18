package org.http4s

import cats._
import cats.data.NonEmptyList
import cats.implicits._
import cats.effect._
import fs2._
import fs2.text._
import java.io.File
import java.net.{InetAddress, InetSocketAddress}
import org.http4s.headers._
import org.log4s.getLogger
import _root_.io.chrisdavenport.vault._

/**
  * Represents a HTTP Message. The interesting subclasses are Request and Response.
  */
sealed trait Message[F[_]] { self =>
  type Self <: Message[F] { type Self = self.Self }

  def httpVersion: HttpVersion

  def headers: Headers

  def body: EntityBody[F]

  def attributes: Vault

  protected def change(
      httpVersion: HttpVersion = httpVersion,
      body: EntityBody[F] = body,
      headers: Headers = headers,
      attributes: Vault = attributes): Self

  def withHttpVersion(httpVersion: HttpVersion): Self =
    change(httpVersion = httpVersion)

  def withHeaders(headers: Headers): Self =
    change(headers = headers)

  def withHeaders(headers: Header*): Self =
    withHeaders(Headers(headers.toList))

  def withAttributes(attributes: Vault): Self =
    change(attributes = attributes)

  // Body methods

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
  def withBodyStream(body: EntityBody[F]): Self =
    change(body = body)

  /** Set an empty entity body on this message, and remove all payload headers
    * that make no sense with an empty body.
    */
  def withEmptyBody: Self =
    withBodyStream(EmptyBody).transformHeaders(_.removePayloadHeaders)

  def bodyAsText(implicit defaultCharset: Charset = DefaultCharset): Stream[F, String] =
    charset.getOrElse(defaultCharset) match {
      case Charset.`UTF-8` =>
        // suspect this one is more efficient, though this is superstition
        body.through(utf8Decode)
      case cs =>
        body.through(util.decode(cs))
    }

  // General header methods

  def transformHeaders(f: Headers => Headers): Self =
    change(headers = f(headers))

  /** Keep headers that satisfy the predicate
    *
    * @param f predicate
    * @return a new message object which has only headers that satisfy the predicate
    */
  def filterHeaders(f: Header => Boolean): Self =
    transformHeaders(_.filter(f))

  def removeHeader(key: HeaderKey): Self =
    filterHeaders(_.isNot(key))

  /** Add the provided headers to the existing headers, replacing those of the same header name
    * The passed headers are assumed to contain no duplicate Singleton headers.
    */
  def putHeaders(headers: Header*): Self =
    transformHeaders(_.put(headers: _*))

  /** Replace the existing headers with those provided */
  @deprecated("Use withHeaders instead", "0.20.0-M2")
  def replaceAllHeaders(headers: Headers): Self =
    withHeaders(headers)

  /** Replace the existing headers with those provided */
  @deprecated("Use withHeaders instead", "0.20.0-M2")
  def replaceAllHeaders(headers: Header*): Self =
    withHeaders(headers: _*)

  def withTrailerHeaders(trailerHeaders: F[Headers]): Self =
    withAttribute(Message.Keys.TrailerHeaders[F], trailerHeaders)

  def withoutTrailerHeaders: Self =
    withoutAttribute(Message.Keys.TrailerHeaders[F])

  /**
    * The trailer headers, as specified in Section 3.6.1 of RFC 2616. The resulting
    * F might not complete until the entire body has been consumed.
    */
  def trailerHeaders(implicit F: Applicative[F]): F[Headers] =
    attributes.lookup(Message.Keys.TrailerHeaders[F]).getOrElse(F.pure(Headers.empty))

  // Specific header methods

  @deprecated("Use withContentType(`Content-Type`(t)) instead", "0.20.0-M2")
  def withType(t: MediaType)(implicit F: Functor[F]): Self =
    withContentType(`Content-Type`(t))

  def withContentType(contentType: `Content-Type`): Self =
    putHeaders(contentType)

  def withoutContentType: Self =
    filterHeaders(_.isNot(`Content-Type`))

  def withContentTypeOption(contentTypeO: Option[`Content-Type`]): Self =
    contentTypeO.fold(withoutContentType)(withContentType)

  def contentType: Option[`Content-Type`] =
    headers.get(`Content-Type`)

  def contentLength: Option[Long] =
    headers.get(`Content-Length`).map(_.length)

  /** Returns the charset parameter of the `Content-Type` header, if present.
    * Does not introspect the body for media types that define a charset
    * internally.
    */
  def charset: Option[Charset] =
    contentType.flatMap(_.charset)

  def isChunked: Boolean =
    headers.get(`Transfer-Encoding`).exists(_.values.contains_(TransferCoding.chunked))

  // Attribute methods

  /** Generates a new message object with the specified key/value pair appended to the [[AttributeMap]]
    *
    * @param key [[Key]] with which to associate the value
    * @param value value associated with the key
    * @tparam A type of the value to store
    * @return a new message object with the key/value pair appended
    */
  def withAttribute[A](key: Key[A], value: A): Self =
    change(attributes = attributes.insert(key, value))

  /**
    * Returns a new message object without the specified key in the [[AttributeMap]]
    *
    * @param key [[Key]] to remove
    * @return a new message object without the key
    */
  def withoutAttribute(key: Key[_]): Self =
    change(attributes = attributes.delete(key))

  // Decoding methods

  /** Decode the [[Message]] to the specified type
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the effect which will generate the `DecodeResult[T]`
    */
  def attemptAs[T](implicit decoder: EntityDecoder[F, T]): DecodeResult[F, T] =
    decoder.decode(this, strict = false)

  /** Decode the [[Message]] to the specified type
    *
    * If no valid [[Status]] has been described, allow Ok
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the effect which will generate the T
    */
  def as[T](implicit F: Functor[F], decoder: EntityDecoder[F, T]): F[T] =
    attemptAs.fold(throw _, identity)

}

object Message {
  private[http4s] val logger = getLogger
  object Keys {
    private[this] val trailerHeaders: Key[Any] = Key.newKey[IO, Any].unsafeRunSync
    def TrailerHeaders[F[_]]: Key[F[Headers]] = trailerHeaders.asInstanceOf[Key[F[Headers]]]
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
    attributes: Vault = Vault.empty
) extends Message[F] {
  import Request._

  type Self = Request[F]

  private def copy(
      method: Method = this.method,
      uri: Uri = this.uri,
      httpVersion: HttpVersion = this.httpVersion,
      headers: Headers = this.headers,
      body: EntityBody[F] = this.body,
      attributes: Vault = this.attributes
  ): Request[F] =
    Request(
      method = method,
      uri = uri,
      httpVersion = httpVersion,
      headers = headers,
      body = body,
      attributes = attributes
    )

  def mapK[G[_]](f: F ~> G): Request[G] =
    Request[G](
      method = method,
      uri = uri,
      httpVersion = httpVersion,
      headers = headers,
      body = body.translate(f),
      attributes = attributes
    )

  def withMethod(method: Method): Self =
    copy(method = method)

  def withUri(uri: Uri): Self =
    copy(uri = uri, attributes = attributes.delete(Request.Keys.PathInfoCaret))

  override protected def change(
      httpVersion: HttpVersion,
      body: EntityBody[F],
      headers: Headers,
      attributes: Vault
  ): Self =
    copy(
      httpVersion = httpVersion,
      body = body,
      headers = headers,
      attributes = attributes
    )

  lazy val (scriptName, pathInfo) = {
    val caret = attributes.lookup(Request.Keys.PathInfoCaret).getOrElse(0)
    uri.path.splitAt(caret)
  }

  def withPathInfo(pi: String): Self =
    withUri(uri.withPath(scriptName + pi))

  def pathTranslated: Option[File] = attributes.lookup(Keys.PathTranslated)

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

  /** Add a Cookie header for the provided [[Cookie]] */
  def addCookie(cookie: RequestCookie): Self =
    putHeaders(Cookie(NonEmptyList.of(cookie)))

  /** Add a Cookie header with the provided values */
  def addCookie(name: String, content: String): Self =
    addCookie(RequestCookie(name, content))

  def authType: Option[AuthScheme] =
    headers.get(Authorization).map(_.credentials.authScheme)

  private def connectionInfo: Option[Connection] = attributes.lookup(Keys.ConnectionInfo)

  def remote: Option[InetSocketAddress] = connectionInfo.map(_.remote)

  /**
    *Returns the the X-Forwarded-For value if present, else the remote address.
    */
  def from: Option[InetAddress] =
    headers
      .get(`X-Forwarded-For`)
      .fold(remote.flatMap(remote => Option(remote.getAddress)))(_.values.head)

  def remoteAddr: Option[String] = remote.map(_.getHostString)
  def remoteHost: Option[String] = remote.map(_.getHostName)
  def remotePort: Option[Int] = remote.map(_.getPort)

  def remoteUser: Option[String] = None

  def server: Option[InetSocketAddress] = connectionInfo.map(_.local)
  def serverAddr: String =
    server
      .map(_.getHostString)
      .orElse(uri.host.map(_.value))
      .orElse(headers.get(Host).map(_.host))
      .getOrElse(InetAddress.getLocalHost.getHostName)

  def serverPort: Int =
    server
      .map(_.getPort)
      .orElse(uri.port)
      .orElse(headers.get(Host).flatMap(_.port))
      .getOrElse(80) // scalastyle:ignore

  /** Whether the Request was received over a secure medium */
  def isSecure: Option[Boolean] = connectionInfo.map(_.secure)

  def serverSoftware: ServerSoftware =
    attributes.lookup(Keys.ServerSoftware).getOrElse(ServerSoftware.Unknown)

  def decodeWith[A](decoder: EntityDecoder[F, A], strict: Boolean)(f: A => F[Response[F]])(
      implicit F: Monad[F]): F[Response[F]] =
    decoder.decode(this, strict = strict).fold(_.toHttpResponse[F](httpVersion), f).flatten

  /** Helper method for decoding [[Request]]s
    *
    * Attempt to decode the [[Request]] and, if successful, execute the continuation to get a [[Response]].
    * If decoding fails, an `UnprocessableEntity` [[Response]] is generated.
    */
  def decode[A](
      f: A => F[Response[F]])(implicit F: Monad[F], decoder: EntityDecoder[F, A]): F[Response[F]] =
    decodeWith(decoder, strict = false)(f)

  /** Helper method for decoding [[Request]]s
    *
    * Attempt to decode the [[Request]] and, if successful, execute the continuation to get a [[Response]].
    * If decoding fails, an `UnprocessableEntity` [[Response]] is generated. If the decoder does not support the
    * [[MediaType]] of the [[Request]], a `UnsupportedMediaType` [[Response]] is generated instead.
    */
  def decodeStrict[A](
      f: A => F[Response[F]])(implicit F: Monad[F], decoder: EntityDecoder[F, A]): F[Response[F]] =
    decodeWith(decoder, strict = true)(f)

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
      attributes: Vault = Vault.empty
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
    val PathInfoCaret: Key[Int] = Key.newKey[IO, Int].unsafeRunSync
    val PathTranslated: Key[File] = Key.newKey[IO, File].unsafeRunSync
    val ConnectionInfo: Key[Connection] = Key.newKey[IO, Connection].unsafeRunSync
    val ServerSoftware: Key[ServerSoftware] = Key.newKey[IO, ServerSoftware].unsafeRunSync
  }
}

/** Representation of the HTTP response to send back to the client
  *
  * @param status [[Status]] code and message
  * @param headers [[Headers]] containing all response headers
  * @param body EntityBody[F] representing the possible body of the response
  * @param attributes [[Vault]] containing additional parameters which may be used by the http4s
  *                   backend for additional processing such as java.io.File object
  */
final case class Response[F[_]](
    status: Status = Status.Ok,
    httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
    headers: Headers = Headers.empty,
    body: EntityBody[F] = EmptyBody,
    attributes: Vault = Vault.empty)
    extends Message[F] {

  type Self = Response[F]

  def mapK[G[_]](f: F ~> G): Response[G] = Response[G](
    status = status,
    httpVersion = httpVersion,
    headers = headers,
    body = body.translate(f),
    attributes = attributes
  )

  def withStatus(status: Status): Self =
    copy(status = status)

  override protected def change(
      httpVersion: HttpVersion,
      body: EntityBody[F],
      headers: Headers,
      attributes: Vault
  ): Self =
    copy(
      httpVersion = httpVersion,
      body = body,
      headers = headers,
      attributes = attributes
    )

  /** Add a Set-Cookie header for the provided [[ResponseCookie]] */
  def addCookie(cookie: ResponseCookie): Self =
    putHeaders(`Set-Cookie`(cookie))

  /** Add a [[`Set-Cookie`]] header with the provided values */
  def addCookie(name: String, content: String, expires: Option[HttpDate] = None): Self =
    addCookie(ResponseCookie(name, content, expires))

  /** Add a [[`Set-Cookie`]] which will remove the specified cookie from the client */
  def removeCookie(cookie: ResponseCookie): Self =
    putHeaders(cookie.clearCookie)

  /** Add a [[`Set-Cookie`]] which will remove the specified cookie from the client */
  def removeCookie(name: String): Self =
    putHeaders(ResponseCookie(name, "").clearCookie)

  /** Returns a list of cookies from the [[`Set-Cookie`]]
    * headers. Includes expired cookies, such as those that represent cookie
    * deletion. */
  def cookies: List[ResponseCookie] =
    `Set-Cookie`.from(headers).map(_.cookie)

  override def toString: String =
    s"""Response(status=${status.code}, headers=${headers.redactSensitive()})"""

}

object Response {
  private[this] val pureNotFound: Response[Pure] =
    Response(
      Status.NotFound,
      body = Stream("Not found").through(text.utf8Encode),
      headers = Headers(`Content-Type`(MediaType.text.plain, Charset.`UTF-8`)))

  def notFound[F[_]]: Response[F] = pureNotFound.copy(body = pureNotFound.body.covary[F])

  def notFoundFor[F[_]: Applicative](request: Request[F])(
      implicit encoder: EntityEncoder[F, String]): F[Response[F]] =
    Response(Status.NotFound).withEntity(s"${request.pathInfo} not found").pure[F]

  def timeout[F[_]]: Response[F] =
    Response[F](Status.ServiceUnavailable).withEntity("Response timed out")
}
