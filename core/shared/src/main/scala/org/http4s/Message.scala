/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import cats.Applicative
import cats.Monad
import cats.data.NonEmptyList
import cats.data.OptionT
import cats.effect.Concurrent
import cats.effect.Sync
import cats.effect.SyncIO
import cats.syntax.all._
import cats.~>
import com.comcast.ip4s.Dns
import com.comcast.ip4s.Hostname
import com.comcast.ip4s.IpAddress
import com.comcast.ip4s.Port
import com.comcast.ip4s.SocketAddress
import fs2.Chunk
import fs2.Pipe
import fs2.Pull
import fs2.Pure
import fs2.Stream
import fs2.io.net.unixsocket.UnixSocketAddress
import fs2.text.utf8
import org.http4s.Message.EntityStreamException
import org.http4s.headers._
import org.http4s.internal.CurlConverter
import org.http4s.syntax.KleisliSyntax
import org.typelevel.ci.CIString
import org.typelevel.vault._

import java.io.File
import scala.util.control.NoStackTrace
import scala.util.hashing.MurmurHash3

/** Represents a HTTP Message. The interesting subclasses are Request and Response.
  */
sealed trait Message[F[_]] extends Media[F] { self =>
  type SelfF[F2[_]] <: Message[F2] { type SelfF[F3[_]] = self.SelfF[F3] }
  type Self = SelfF[F]

  def httpVersion: HttpVersion

  def headers: Headers

  def body: EntityBody[F]

  def attributes: Vault

  protected def change(
      httpVersion: HttpVersion = httpVersion,
      body: EntityBody[F] = body,
      headers: Headers = headers,
      attributes: Vault = attributes,
  ): Self

  def withHttpVersion(httpVersion: HttpVersion): Self =
    change(httpVersion = httpVersion)

  def withHeaders(headers: Headers): Self =
    change(headers = headers)

  def withHeaders(headers: Header.ToRaw*): Self =
    withHeaders(Headers(headers: _*))

  def withAttributes(attributes: Vault): Self =
    change(attributes = attributes)

  // Body methods

  /** Replace the body of this message with a new body
    *
    * @param b body to attach to this method
    * @param w [[EntityEncoder]] with which to convert the body to an [[EntityBody]]
    * @tparam T type of the Body
    * @return a new message with the new body
    */
  def withEntity[T](b: T)(implicit w: EntityEncoder[F, T]): Self = {
    val entity = w.toEntity(b)

    val hsBase = headers ++ w.headers
    val cl: Option[`Content-Length`] = entity.length match {
      case Some(l) =>
        `Content-Length`
          .fromLong(l)
          .fold(
            _ => {
              Message.logger
                .warn(s"Attempt to provide a negative content length of $l")
                .unsafeRunSync()
              None
            },
            Some(_),
          )
      case None => None
    }

    change(body = entity.body, headers = cl.fold(hsBase)(hsBase.withContentLength))
  }

  /** Sets the entity body without affecting headers such as `Transfer-Encoding`
    * or `Content-Length`. Most use cases are better served by [[withEntity]],
    * which uses an [[EntityEncoder]] to maintain the headers.
    *
    * WARNING: this method does not modify the headers of the message, and as
    * a consequence headers may be incoherent with the body.
    */
  def withBodyStream(body: EntityBody[F]): Self =
    change(body = body)

  /** Set an empty entity body on this message, and remove all payload headers
    * that make no sense with an empty body.
    */
  def withEmptyBody: Self =
    withBodyStream(EmptyBody).transformHeaders(_.removePayloadHeaders)

  /** Applies the given pipe to the entity body (byte-stream) of this message.
    *
    * WARNING: this method does not modify the headers of the message, and as
    * a consequence headers may be incoherent with the body.
    */
  private[http4s] def pipeBodyThrough(pipe: Pipe[F, Byte, Byte]): Self =
    withBodyStream(pipe(body))

  // General header methods

  def transformHeaders(f: Headers => Headers): Self =
    change(headers = f(headers))

  /** Keep headers that satisfy the predicate
    *
    * @param f predicate
    * @return a new message object which has only headers that satisfy the predicate
    */
  def filterHeaders(f: Header.Raw => Boolean): Self =
    transformHeaders(_.transform(_.filter(f)))

  def removeHeader(key: CIString): Self =
    transformHeaders(_.transform(_.filterNot(_.name == key)))

  def removeHeader[A](implicit h: Header[A, _]): Self =
    removeHeader(h.name)

  /** Add the provided headers to the existing headers, replacing those
    * of the same header name
    *
    * {{{
    * >>> import cats.effect.IO
    * >>> import org.http4s.headers.Accept
    *
    * >>> val req = Request[IO]().putHeaders(Accept(MediaRange.`application/*`))
    * >>> req.headers.get[Accept]
    * Some(Accept(NonEmptyList(application/*)))
    *
    * >>> val req2 = req.putHeaders(Accept(MediaRange.`text/*`))
    * >>> req2.headers.get[Accept]
    * Some(Accept(NonEmptyList(text/*)))
    * }}}
    * */*/*/*/
    */
  def putHeaders(headers: Header.ToRaw*): Self =
    transformHeaders(_.put(headers: _*))

  /** Add a header to these headers.  The header should be a type with a
    * recurring `Header` instance to ensure that the new value can be
    * appended to any existing values.
    *
    * {{{
    * >>> import cats.effect.IO
    * >>> import org.http4s.headers.Accept
    *
    * >>> val req = Request[IO]().addHeader(Accept(MediaRange.`application/*`))
    * >>> req.headers.get[Accept]
    * Some(Accept(NonEmptyList(application/*)))
    *
    * >>> val req2 = req.addHeader(Accept(MediaRange.`text/*`))
    * >>> req2.headers.get[Accept]
    * Some(Accept(NonEmptyList(application/*, text/*)))
    * }}}
    * */*/*/*/*/
    */
  def addHeader[H: Header[*, Header.Recurring]](h: H): Self =
    transformHeaders(_.add(h))

  def withTrailerHeaders(trailerHeaders: F[Headers]): Self =
    withAttribute(Message.Keys.TrailerHeaders[F], trailerHeaders)

  def withoutTrailerHeaders: Self =
    withoutAttribute(Message.Keys.TrailerHeaders[F])

  /** The trailer headers, as specified in Section 3.6.1 of RFC 2616. The resulting
    * F might not complete until the entire body has been consumed.
    */
  def trailerHeaders(implicit F: Applicative[F]): F[Headers] =
    attributes.lookup(Message.Keys.TrailerHeaders[F]).getOrElse(Headers.empty.pure[F])

  // Specific header methods

  def withContentType(contentType: `Content-Type`): Self =
    putHeaders(contentType)

  def withoutContentType: Self =
    removeHeader[`Content-Type`]

  def withContentTypeOption(contentTypeO: Option[`Content-Type`]): Self =
    contentTypeO.fold(withoutContentType)(withContentType)

  def isChunked: Boolean =
    headers.get[`Transfer-Encoding`].exists(_.values.contains_(TransferCoding.chunked))

  // Attribute methods

  /** Generates a new message object with the specified key/value pair appended
    * to the [[attributes]].
    *
    * @param key [[org.typelevel.vault.Key]] with which to associate the value
    * @param value value associated with the key
    * @tparam A type of the value to store
    * @return a new message object with the key/value pair appended
    */
  def withAttribute[A](key: Key[A], value: A): Self =
    change(attributes = attributes.insert(key, value))

  /** Returns a new message object without the specified key in the
    * [[attributes]].
    *
    * @param key [[org.typelevel.vault.Key]] to remove
    * @return a new message object without the key
    */
  def withoutAttribute(key: Key[_]): Self =
    change(attributes = attributes.delete(key))

  /** Lifts this Message's body to the specified effect type.
    */
  override def covary[F2[x] >: F[x]]: SelfF[F2] = this.asInstanceOf[SelfF[F2]]

  /** Compiles the body stream to a single chunk and sets it as the
    * body. Replaces any `Transfer-Encoding: chunked` with a
    * `Content-Length` header. It is the caller's responsibility to
    * assure there is enough memory to materialize the entity body and
    * control the time limits of that materialization.
    *
    * @param maxBytes maximum length of the entity stream. If the stream
    *                 exceeds the limit then processing fails with the [[Message.EntityStreamException]].
    *                 Pass the [[scala.None]] if you don't want to limit the entity body.
    */
  def toStrict(maxBytes: Option[Long])(implicit F: Concurrent[F]): F[Self] = {
    def withLimit(entityBody: EntityBody[F]) = maxBytes match {
      case Some(limit) =>
        entityBody.pull
          .take(limit)
          .flatMap {
            case Some(_) =>
              Pull.raiseError[F](EntityStreamException.createWithDefaultMsg(limit))
            case None =>
              Pull.done
          }
          .stream

      case None =>
        entityBody
    }

    withLimit(body).compile.to(Chunk).map { chunk =>
      withBodyStream(Stream.chunk(chunk))
        .transformHeaders(_.withContentLength(`Content-Length`.unsafeFromLong(chunk.size.toLong)))
    }
  }
}

object Message {
  final case class EntityStreamException(msg: String) extends Exception(msg) with NoStackTrace

  object EntityStreamException {
    def createWithDefaultMsg(maxBytes: Long): EntityStreamException =
      EntityStreamException(s"Entity stream has exceeded the maximum of $maxBytes bytes")
  }

  private[http4s] val logger = Platform.loggerFactory.getLogger
  object Keys {
    private[this] val trailerHeaders: Key[Any] = Key.newKey[SyncIO, Any].unsafeRunSync()
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
final class Request[F[_]] private (
    val method: Method,
    val uri: Uri,
    val httpVersion: HttpVersion,
    val headers: Headers,
    val body: EntityBody[F],
    val attributes: Vault,
) extends Message[F]
    with Product
    with Serializable {
  import Request._

  type SelfF[F0[_]] = Request[F0]

  private def copy(
      method: Method = this.method,
      uri: Uri = this.uri,
      httpVersion: HttpVersion = this.httpVersion,
      headers: Headers = this.headers,
      body: EntityBody[F] = this.body,
      attributes: Vault = this.attributes,
  ): Request[F] =
    Request(
      method = method,
      uri = uri,
      httpVersion = httpVersion,
      headers = headers,
      body = body,
      attributes = attributes,
    )

  def mapK[G[_]](f: F ~> G): Request[G] =
    Request[G](
      method = method,
      uri = uri,
      httpVersion = httpVersion,
      headers = headers,
      body = body.translate(f),
      attributes = attributes,
    )

  def withMethod(method: Method): Request[F] =
    copy(method = method)

  def withUri(uri: Uri): Request[F] =
    copy(uri = uri, attributes = attributes.delete(Request.Keys.PathInfoCaret))

  override protected def change(
      httpVersion: HttpVersion,
      body: EntityBody[F],
      headers: Headers,
      attributes: Vault,
  ): Request[F] =
    copy(
      httpVersion = httpVersion,
      body = body,
      headers = headers,
      attributes = attributes,
    )

  lazy val (scriptName, pathInfo) = {
    val (l, r) = uri.path.splitAt(caret)
    (l.toAbsolute, r.toAbsolute)
  }

  private def caret =
    attributes.lookup(Request.Keys.PathInfoCaret).getOrElse(-1)

  @deprecated(message = "Use {withPathInfo(Uri.Path)} instead", since = "0.22.0-M1")
  def withPathInfo(pi: String): Request[F] =
    withPathInfo(Uri.Path.unsafeFromString(pi))
  def withPathInfo(pi: Uri.Path): Request[F] =
    // Don't use withUri, which clears the caret
    copy(uri = uri.withPath(scriptName.concat(pi)))

  def pathTranslated: Option[File] = attributes.lookup(Keys.PathTranslated)

  def queryString: String = uri.query.renderString

  /** cURL representation of the request.
    *
    * Supported cURL-Parameters are: --request, --url, --header.
    * Note that `asCurl` will not print the request body.
    */
  def asCurl(redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains): String =
    CurlConverter.requestToCurlWithoutBody(this, redactHeadersWhen)

  /** cURL representation of the request and a new request
    *
    * Supported cURL-Parameters are: --request, --url, --header --data.
    * Note that `asCurlWith` will print the request body, which may have privacy implications.
    *
    * This method will consume the body create a new request with the cached body to avoid issues
    * this may cause, which may consume large amounts of memory to cache a request that streams a
    * large amount of data.
    */
  def asCurlWithBody(
      redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains
  )(implicit
      concurrent: Concurrent[F],
      defaultCharset: Charset = Charset.`UTF-8`,
  ): F[(String, Request[F])] =
    CurlConverter.requestToCurlWithBody(this, redactHeadersWhen)

  /** Representation of the query string as a map
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
    * <tr><td><code>?p1=v1&amp;p1=v2&amp;p2=v3&amp;p2=v4</code></td><td><code>Map("p1" -> Seq("v1","v2"), "p2" -> Seq("v3","v4"))</code></td></tr>
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

  /** Parses all available [[org.http4s.headers.Cookie]] headers into a list of
    * [[RequestCookie]] objects. This implementation is compatible with cookie
    * headers formatted per HTTP/1 and HTTP/2, or even both at the same time.
    */
  def cookies: List[RequestCookie] =
    headers.get[Cookie].fold(List.empty[RequestCookie])(_.values.toList)

  /** Add a Cookie header for the provided [[org.http4s.headers.Cookie]] */
  def addCookie(cookie: RequestCookie): Request[F] =
    putHeaders {
      headers
        .get[Cookie]
        .fold(Cookie(NonEmptyList.of(cookie))) { preExistingCookie =>
          Cookie(preExistingCookie.values.append(cookie))
        }
    }

  /** Add a Cookie header with the provided values */
  def addCookie(name: String, content: String): Request[F] =
    addCookie(RequestCookie(name, content))

  def authType: Option[AuthScheme] =
    headers.get[Authorization].map(_.credentials.authScheme)

  private def connectionInfo: Option[Connection] = attributes.lookup(Keys.ConnectionInfo)

  def remote: Option[SocketAddress[IpAddress]] = connectionInfo.map(_.remote)

  /** Returns the the X-Forwarded-For value if present, else the remote address.
    */
  def from: Option[IpAddress] =
    headers
      .get[`X-Forwarded-For`]
      .fold(remote.map(_.host))(_.values.head)

  def remoteAddr: Option[IpAddress] = remote.map(_.host)

  @deprecated("Use the variant requiring ip4s.Dns[F]", "0.23.7")
  def remoteHost(F: Sync[F]): F[Option[Hostname]] = {
    val inetAddress = remote.map(_.host.toInetAddress)
    F.map(F.blocking(inetAddress.map(_.getHostName)))(_.flatMap(Hostname.fromString))
  }

  def remoteHost(implicit F: Monad[F], dns: Dns[F]): F[Option[Hostname]] =
    OptionT.fromOption(remote.map(_.host)).flatMapF(dns.reverseOption).value

  def remotePort: Option[Port] = remote.map(_.port)

  def remoteUser: Option[String] = None

  def server: Option[SocketAddress[IpAddress]] = connectionInfo.map(_.local)

  def serverAddr: Option[IpAddress] =
    server
      .map(_.host)
      .orElse(uri.host.flatMap(_.toIpAddress))
      .orElse(headers.get[Host].flatMap(h => IpAddress.fromString(h.host)))

  def serverPort: Option[Port] =
    server
      .map(_.port)
      .orElse(uri.port.flatMap(Port.fromInt))
      .orElse(headers.get[Host].flatMap(_.port.flatMap(Port.fromInt)))

  /** Whether the Request was received over a secure medium */
  def isSecure: Option[Boolean] = connectionInfo.map(_.secure)

  def serverSoftware: ServerSoftware =
    attributes.lookup(Keys.ServerSoftware).getOrElse(ServerSoftware.Unknown)

  /** A request is idempotent if its method is idempotent or it contains
    * an `Idempotency-Key` header.
    */
  def isIdempotent: Boolean =
    method.isIdempotent || headers.contains[`Idempotency-Key`]

  def decodeWith[A](decoder: EntityDecoder[F, A], strict: Boolean)(
      f: A => F[Response[F]]
  )(implicit F: Monad[F]): F[Response[F]] =
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
      f: A => F[Response[F]]
  )(implicit F: Monad[F], decoder: EntityDecoder[F, A]): F[Response[F]] =
    decodeWith(decoder, strict = false)(f)

  /** Helper method for decoding [[Request]]s
    *
    * Attempt to decode the [[Request]] and, if successful, execute the continuation to get a [[Response]].
    * If decoding fails, an `UnprocessableEntity` [[Response]] is generated. If the decoder does not support the
    * [[MediaType]] of the [[Request]], a `UnsupportedMediaType` [[Response]] is generated instead.
    */
  def decodeStrict[A](
      f: A => F[Response[F]]
  )(implicit F: Monad[F], decoder: EntityDecoder[F, A]): F[Response[F]] =
    decodeWith(decoder, strict = true)(f)

  override def hashCode(): Int = MurmurHash3.productHash(this)

  def canEqual(that: Any): Boolean = that match {
    case _: Request[_] => true
    case _ => false
  }

  def productArity: Int = 6

  def productElement(n: Int): Any =
    n match {
      case 0 => method
      case 1 => uri
      case 2 => httpVersion
      case 3 => headers
      case 4 => body
      case 5 => attributes
      case _ => throw new IndexOutOfBoundsException()
    }

  /** A projection of this request without the body. */
  def requestPrelude: RequestPrelude =
    RequestPrelude.fromRequest(this)

  override def toString: String =
    s"""Request(method=$method, uri=$uri, httpVersion=${httpVersion}, headers=${headers
        .redactSensitive()})"""
}

object Request {

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
  def apply[F[_]](
      method: Method = Method.GET,
      uri: Uri = Uri(path = Uri.Path.Root),
      httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
      headers: Headers = Headers.empty,
      body: EntityBody[F] = EmptyBody,
      attributes: Vault = Vault.empty,
  ): Request[F] =
    new Request[F](
      method = method,
      uri = uri,
      httpVersion = httpVersion,
      headers = headers,
      body = body,
      attributes = attributes,
    )

  def unapply[F[_]](
      request: Request[F]
  ): Option[(Method, Uri, HttpVersion, Headers, EntityBody[F], Vault)] =
    Some(
      (
        request.method,
        request.uri,
        request.httpVersion,
        request.headers,
        request.body,
        request.attributes,
      )
    )

  final case class Connection(
      local: SocketAddress[IpAddress],
      remote: SocketAddress[IpAddress],
      secure: Boolean,
  )

  object Keys {
    val PathInfoCaret: Key[Int] = Key.newKey[SyncIO, Int].unsafeRunSync()
    val PathTranslated: Key[File] = Key.newKey[SyncIO, File].unsafeRunSync()
    val ConnectionInfo: Key[Connection] = Key.newKey[SyncIO, Connection].unsafeRunSync()
    val ServerSoftware: Key[ServerSoftware] = Key.newKey[SyncIO, ServerSoftware].unsafeRunSync()
    val UnixSocketAddress: Key[UnixSocketAddress] =
      Key.newKey[SyncIO, UnixSocketAddress].unsafeRunSync()
  }
}

/** Representation of the HTTP response to send back to the client
  *
  * @param status [[Status]] code and message
  * @param headers [[Headers]] containing all response headers
  * @param body EntityBody[F] representing the possible body of the response
  * @param attributes [[org.typelevel.vault.Vault]] containing additional
  *                   parameters which may be used by the http4s backend for
  *                   additional processing such as java.io.File object
  */
final class Response[F[_]] private (
    val status: Status,
    val httpVersion: HttpVersion,
    val headers: Headers,
    val body: EntityBody[F],
    val attributes: Vault,
) extends Message[F]
    with Product
    with Serializable {
  type SelfF[F0[_]] = Response[F0]

  def mapK[G[_]](f: F ~> G): Response[G] =
    Response[G](
      status = status,
      httpVersion = httpVersion,
      headers = headers,
      body = body.translate(f),
      attributes = attributes,
    )

  def withStatus(status: Status): Response[F] =
    copy(status = status)

  override protected def change(
      httpVersion: HttpVersion,
      body: EntityBody[F],
      headers: Headers,
      attributes: Vault,
  ): Response[F] =
    copy(
      httpVersion = httpVersion,
      body = body,
      headers = headers,
      attributes = attributes,
    )

  /** Add a Set-Cookie header for the provided [[ResponseCookie]] */
  def addCookie(cookie: ResponseCookie): Response[F] =
    transformHeaders(_.add(`Set-Cookie`(cookie)))

  /** Add a [[org.http4s.headers.`Set-Cookie`]] header with the provided values */
  def addCookie(name: String, content: String, expires: Option[HttpDate] = None): Response[F] =
    addCookie(ResponseCookie(name, content, expires))

  /** Add a [[org.http4s.headers.`Set-Cookie`]] which will remove the specified
    * cookie from the client
    */
  def removeCookie(cookie: ResponseCookie): Response[F] =
    addCookie(cookie.clearCookie)

  /** Add a [[org.http4s.headers.`Set-Cookie`]] which will remove the specified cookie from the client */
  def removeCookie(name: String): Response[F] =
    addCookie(ResponseCookie(name, "").clearCookie)

  /** Returns a list of cookies from the [[org.http4s.headers.`Set-Cookie`]]
    * headers. Includes expired cookies, such as those that represent cookie
    * deletion.
    */
  def cookies: List[ResponseCookie] =
    headers.get[`Set-Cookie`].foldMap(_.toList).map(_.cookie)

  override def hashCode(): Int = MurmurHash3.productHash(this)

  def copy(
      status: Status = this.status,
      httpVersion: HttpVersion = this.httpVersion,
      headers: Headers = this.headers,
      body: EntityBody[F] = this.body,
      attributes: Vault = this.attributes,
  ): Response[F] =
    Response[F](
      status = status,
      httpVersion = httpVersion,
      headers = headers,
      body = body,
      attributes = attributes,
    )

  def canEqual(that: Any): Boolean =
    that match {
      case _: Response[_] => true
      case _ => false
    }

  def productArity: Int = 5

  def productElement(n: Int): Any =
    n match {
      case 0 => status
      case 1 => httpVersion
      case 2 => headers
      case 3 => body
      case 4 => attributes
      case _ => throw new IndexOutOfBoundsException()
    }

  /** A projection of this response without the body. */
  def responsePrelude: ResponsePrelude =
    ResponsePrelude.fromResponse(this)

  override def toString: String =
    s"""Response(status=${status.code}, httpVersion=${httpVersion}, headers=${headers
        .redactSensitive()})"""
}

object Response extends KleisliSyntax {

  /** Representation of the HTTP response to send back to the client
    *
    * @param status [[Status]] code and message
    * @param headers [[Headers]] containing all response headers
    * @param body EntityBody[F] representing the possible body of the response
    * @param attributes [[org.typelevel.vault.Vault]] containing additional
    *                   parameters which may be used by the http4s backend for
    *                   additional processing such as java.io.File object
    */
  def apply[F[_]](
      status: Status = Status.Ok,
      httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
      headers: Headers = Headers.empty,
      body: EntityBody[F] = EmptyBody,
      attributes: Vault = Vault.empty,
  ): Response[F] =
    new Response(status, httpVersion, headers, body, attributes)

  def unapply[F[_]](
      response: Response[F]
  ): Option[(Status, HttpVersion, Headers, EntityBody[F], Vault)] =
    Some(
      (response.status, response.httpVersion, response.headers, response.body, response.attributes)
    )

  private[this] val pureNotFound: Response[Pure] =
    Response(
      Status.NotFound,
      body = Stream("Not found").through(utf8.encode),
      headers = Headers(
        `Content-Type`(MediaType.text.plain, Charset.`UTF-8`),
        `Content-Length`.unsafeFromLong(9L),
      ),
    )

  def notFound[F[_]]: Response[F] = pureNotFound.covary[F]

  def notFoundFor[F[_]: Applicative](request: Request[F])(implicit
      encoder: EntityEncoder[F, String]
  ): F[Response[F]] =
    Response[F](Status.NotFound).withEntity(s"${request.pathInfo} not found").pure[F]

  def timeout[F[_]]: Response[F] =
    Response[F](Status.ServiceUnavailable).withEntity("Response timed out")
}
