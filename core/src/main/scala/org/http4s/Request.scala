package org.http4s

import java.io.File
import java.net.{InetAddress, InetSocketAddress}

import cats.data.NonEmptyList
import cats.Monad
import cats.implicits._

import org.http4s.headers.{Authorization, Host, `X-Forwarded-For`}
import org.http4s.server.ServerSoftware
import Message.messSyntax._

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
abstract case class Request[F[_]](
                                          method: Method = Method.GET,
                                          uri: Uri = Uri(path = "/"),
                                          httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
                                          headers: Headers = Headers.empty,
                                          body: EntityBody[F] = EmptyBody,
                                          attributes: AttributeMap = AttributeMap.empty
                                        ) {
  import Request._

  private[http4s] def requestCopy(
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

  def withMethod(method: Method) = requestCopy(method = method)
  def withUri(uri: Uri) =
    requestCopy(uri = uri, attributes = attributes -- Request.Keys.PathInfoCaret)
  def withHttpVersion(httpVersion: HttpVersion) = requestCopy(httpVersion = httpVersion)
  def withHeaders(headers: Headers) = requestCopy(headers = headers)
  def withAttributes(attributes: AttributeMap) = requestCopy(attributes = attributes)

  def withBodyStream(body: EntityBody[F]): Request[F] =
    requestCopy(body = body)

  lazy val authType: Option[AuthScheme] = headers.get(Authorization).map(_.credentials.authScheme)

  lazy val (scriptName, pathInfo) = {
    val caret = attributes.get(Request.Keys.PathInfoCaret).getOrElse(0)
    uri.path.splitAt(caret)
  }

  def withPathInfo(pi: String): Request[F] =
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

  /** Like [[decode]], but with an explicit decoder.
    * @param strict If strict, will return a [[Status.UnsupportedMediaType]] http Response if this message's
    *               [[MediaType]] is not supported by the provided decoder
    */
  def decodeWith[A](decoder: EntityDecoder[F, A], strict: Boolean)(f: A => F[Response[F]])(
    implicit F: Monad[F]): F[Response[F]] =
    decoder.decode(this, strict).fold(_.toHttpResponse[F](httpVersion), f).flatten

  override def toString: String =
    s"""Request(method=$method, uri=$uri, headers=${headers.redactSensitive()})"""

  /** Helper method for decoding [[Request]]s
    *
    * Attempt to decode the [[Request]] and, if successful, execute the continuation to get a [[Response]].
    * If decoding fails, an `UnprocessableEntity` [[Response]] is generated.
    */
  final def decode[A](
                       f: A => F[Response[F]])(implicit F: Monad[F], decoder: EntityDecoder[F, A]): F[Response[F]] =
    decodeWith(decoder, strict = false)(f)

  /** Helper method for decoding [[Request]]s
    *
    * Attempt to decode the [[Request]] and, if successful, execute the continuation to get a [[Response]].
    * If decoding fails, an `UnprocessableEntity` [[Response]] is generated. If the decoder does not support the
    * [[MediaType]] of the [[Request]], a `UnsupportedMediaType` [[Response]] is generated instead.
    */
  final def decodeStrict[A](
                             f: A => F[Response[F]])(implicit F: Monad[F], decoder: EntityDecoder[F, A]): F[Response[F]] =
    decodeWith(decoder, true)(f)


  /** Add a Cookie header for the provided [[Cookie]] */
  final def addCookie(cookie: Cookie): Request[F] =
    this.putHeaders(org.http4s.headers.Cookie(NonEmptyList.of(cookie)))

  /** Add a Cookie header with the provided values */
  final def addCookie(name: String, content: String, expires: Option[HttpDate] = None): Request[F] =
    addCookie(Cookie(name, content, expires))
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

  implicit def requestInstance[F[_]]: Message[Request, F] = new Message[Request, F] {
    override def httpVersion(m: Request[F]): HttpVersion = m.httpVersion
    override def headers(m: Request[F]): Headers = m.headers
    override def body(m: Request[F]): EntityBody[F] = m.body
    override def attributes(m: Request[F]): AttributeMap = m.attributes
    override def change(m: Request[F])(
      body: EntityBody[F],
      headers: Headers,
      attributes: AttributeMap
    ): Request[F] =
      Request[F](
        method = m.method,
        uri = m.uri,
        httpVersion = m.httpVersion,
        headers = headers,
        body = body,
        attributes = attributes
      )
  }

  final case class Connection(local: InetSocketAddress, remote: InetSocketAddress, secure: Boolean)

  object Keys {
    val PathInfoCaret = AttributeKey[Int]
    val PathTranslated = AttributeKey[File]
    val ConnectionInfo = AttributeKey[Connection]
    val ServerSoftware = AttributeKey[ServerSoftware]
  }
}
