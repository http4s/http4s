package org.http4s

import org.http4s.headers._
import io.chrisdavenport.vault._
import cats._
import cats.implicits._
import cats.effect._
import java.net.{InetAddress, InetSocketAddress}
import org.http4s.util.CaseInsensitiveString
import cats.data.NonEmptyList
import java.io.File
import scala.util.hashing.MurmurHash3

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
final class Request[F[_]](
    val method: Method = Method.GET,
    val uri: Uri = Uri(path = "/"),
    val httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
    val headers: Headers = Headers.empty,
    val body: EntityBody[F] = EmptyBody,
    val attributes: Vault = Vault.empty
) extends Product
    with Serializable {
  import Request._

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

  def withMethod(method: Method): Request[F] =
    copy(method = method)

  def withUri(uri: Uri): Request[F] =
    copy(uri = uri, attributes = attributes.delete(Request.Keys.PathInfoCaret))

  def change(
      httpVersion: HttpVersion,
      body: EntityBody[F],
      headers: Headers,
      attributes: Vault
  ): Request[F] =
    copy(
      httpVersion = httpVersion,
      body = body,
      headers = headers,
      attributes = attributes
    )

  lazy val (scriptName, pathInfo) =
    uri.path.splitAt(caret)

  private def caret =
    attributes.lookup(Request.Keys.PathInfoCaret).getOrElse(0)

  def withPathInfo(pi: String): Request[F] =
    // Don't use withUri, which clears the caret
    copy(uri = uri.withPath(scriptName + pi))

  def pathTranslated: Option[File] = attributes.lookup(Keys.PathTranslated)

  def queryString: String = uri.query.renderString

  /** cURL representation of the request.
    *
    * Supported cURL-Parameters are: -X, -H
    *
    */
  def asCurl(
      redactHeadersWhen: CaseInsensitiveString => Boolean = Headers.SensitiveHeaders.contains)
      : String = {

    /*
     * escapes characters that are used in the curl-command, such as '
     */
    def escapeQuotationMarks(s: String) = s.replaceAll("'", """'\\''""")

    val elements = List(
      s"-X ${method.name}",
      s"'${escapeQuotationMarks(uri.renderString)}'",
      headers
        .redactSensitive(redactHeadersWhen)
        .toList
        .map { header =>
          s"-H '${escapeQuotationMarks(header.toString)}'"
        }
        .mkString(" ")
    )
    s"curl ${elements.filter(_.nonEmpty).mkString(" ")}"
  }

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

  /**
    * Parses all available [[Cookie]] headers into a list of [[RequestCookie]] objects. This
    * implementation is compatible with cookie headers formatted per HTTP/1 and HTTP/2, or even both
    * at the same time.
    */
  def cookies: List[RequestCookie] =
    headers.get(Cookie).fold(List.empty[RequestCookie])(_.values.toList)

  /** Add a Cookie header for the provided [[Cookie]] */
  def addCookie(cookie: RequestCookie): Request[F] =
    headers
      .get(Cookie)
      .fold(Request.message.putHeaders(this, Cookie(NonEmptyList.of(cookie)))) { preExistingCookie =>
        Request.message.putHeaders(
          Request.message.removeHeader(this, Cookie),
          Header(
            Cookie.name.value,
            s"${preExistingCookie.value}; ${cookie.name}=${cookie.content}"))
      }

  /** Add a Cookie header with the provided values */
  def addCookie(name: String, content: String): Request[F] =
    addCookie(RequestCookie(name, content))

  def authType: Option[AuthScheme] =
    headers.get(Authorization).map(_.credentials.authScheme)

  private def connectionInfo: Option[Connection] = attributes.lookup(Keys.ConnectionInfo)

  def remote: Option[InetSocketAddress] = connectionInfo.map(_.remote)

  /**
    * Returns the the X-Forwarded-For value if present, else the remote address.
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
    decoder
      .decode(this, strict = strict)
      .fold(_.toHttpResponse[F](httpVersion).pure[F], f)
      .flatten

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

  override def hashCode(): Int = MurmurHash3.productHash(this)

  override def equals(that: Any): Boolean =
    (this eq that.asInstanceOf[Object]) || (that match {
      case that: Request[_] =>
        (this.method == that.method) &&
          (this.uri == that.uri) &&
          (this.httpVersion == that.httpVersion) &&
          (this.headers == that.headers) &&
          (this.body == that.body) &&
          (this.attributes == that.attributes)
      case _ => false
    })

  def canEqual(that: Any): Boolean = that.isInstanceOf[Request[F]]

  def productArity: Int = 6

  def productElement(n: Int): Any = n match {
    case 0 => method
    case 1 => uri
    case 2 => httpVersion
    case 3 => headers
    case 4 => body
    case 5 => attributes
    case _ => throw new IndexOutOfBoundsException()
  }

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
    )

  implicit val message = new Message[Request]{
    def attributes[F[_]](m: Request[F]): Vault = m.attributes
    def body[F[_]](m: Request[F]): org.http4s.EntityBody[F] = m.body
    def httpVersion[F[_]](m: Request[F]): HttpVersion = m.httpVersion
    def headers[F[_]](m: Request[F]): Headers = m.headers

    def change[F[_]](m: Request[F])(httpVersion: HttpVersion, body: org.http4s.EntityBody[F], headers: Headers, attributes: Vault): Request[F] = 
      m.change(httpVersion, body, headers, attributes)
  }

  def unapply[F[_]](
      message: Request[F]): Option[(Method, Uri, HttpVersion, Headers, EntityBody[F], Vault)] =
    message match {
      case request: Request[F] =>
        Some(
          (
            request.method,
            request.uri,
            request.httpVersion,
            request.headers,
            request.body,
            request.attributes))
      case _ => None
    }

  final case class Connection(local: InetSocketAddress, remote: InetSocketAddress, secure: Boolean)

  object Keys {
    val PathInfoCaret: Key[Int] = Key.newKey[IO, Int].unsafeRunSync
    val PathTranslated: Key[File] = Key.newKey[IO, File].unsafeRunSync
    val ConnectionInfo: Key[Connection] = Key.newKey[IO, Connection].unsafeRunSync
    val ServerSoftware: Key[ServerSoftware] = Key.newKey[IO, ServerSoftware].unsafeRunSync
  }
}