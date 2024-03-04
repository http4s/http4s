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

import cats._
import cats.data.NonEmptyList
import cats.effect.Concurrent
import cats.effect.SyncIO
import cats.syntax.all._
import com.comcast.ip4s.Dns
import com.comcast.ip4s.Hostname
import com.comcast.ip4s.IpAddress
import com.comcast.ip4s.Port
import com.comcast.ip4s.SocketAddress
import fs2.Pipe
import fs2.Pull
import fs2.Pure
import fs2.Stream
import fs2.io.net.unixsocket.UnixSocketAddress
import org.http4s.Message.EntityStreamException
import org.http4s.headers._
import org.http4s.internal.CurlConverter
import org.http4s.syntax.KleisliSyntax
import org.typelevel.ci.CIString
import org.typelevel.vault._
import scodec.bits._

import java.io.File
import scala.collection.immutable.Map
import scala.util.control.NoStackTrace
import scala.util.hashing.MurmurHash3

/** Represents a HTTP Message. The interesting subclasses are Request and Response.
  */
sealed trait Message[+F[_]] extends Media[F] { self =>
  type SelfF[+F2[_]] <: Message[F2] { type SelfF[+F3[_]] = self.SelfF[F3] }

  def httpVersion: HttpVersion

  def attributes: Vault

  protected def change[F1[_]](
      httpVersion: HttpVersion = httpVersion,
      entity: Entity[F1] = entity,
      headers: Headers = headers,
      attributes: Vault = attributes,
  ): SelfF[F1]

  def withHttpVersion(httpVersion: HttpVersion): SelfF[F] =
    change(httpVersion = httpVersion)

  def withHeaders(headers: Headers): SelfF[F] =
    change(headers = headers)

  def withHeaders(headers: Header.ToRaw*): SelfF[F] =
    withHeaders(Headers(headers: _*))

  def withAttributes(attributes: Vault): SelfF[F] =
    change(attributes = attributes)

  // Body methods

  /** Replace the body of this message with a new body
    *
    * @param b body to attach to this method
    * @param w [[EntityEncoder]] with which to convert the body to an [[Entity]]
    * @tparam T type of the Body
    * @return a new message with the new body
    */
  def withEntity[F1[x] >: F[x], T](b: T)(implicit w: EntityEncoder[F1, T]): SelfF[F1] = {
    val entity = w.toEntity(b)

    val hsBase = headers ++ w.headers
    val cl: Option[`Content-Length`] = entity.length.flatMap(
      `Content-Length`
        .fromLong(_)
        .toOption
    )
    change(entity = entity, headers = cl.fold(hsBase)(hsBase.withContentLength))
  }

  def withEntity[F1[x]](entity: Entity[F1]): SelfF[F1] =
    change(entity = entity)

  /** Sets the entity body without affecting headers such as `Transfer-Encoding`
    * or `Content-Length`. Most use cases are better served by [[[[withEntity[F1[x]](entity*]]]],
    * which uses an [[EntityEncoder]] to maintain the headers.
    *
    * WARNING: this method does not modify the headers of the message, and as
    * a consequence headers may be incoherent with the body.
    */
  def withBodyStream[F1[x] >: F[x]](body: EntityBody[F1]): SelfF[F1] =
    change(entity = Entity.stream(body))

  /** Set an [[Entity.Empty]] entity on this message, and remove all payload headers
    * that make no sense with an empty body.
    */
  def withEmptyBody: SelfF[Pure] =
    change(entity = Entity.Empty).transformHeaders(_.removePayloadHeaders)

  /** Applies the given pipe to the entity body (byte-stream) of this message.
    *
    * WARNING: this method does not modify the headers of the message, and as
    * a consequence headers may be incoherent with the body.
    */
  private[http4s] def pipeBodyThrough[F1[x] >: F[x]](pipe: Pipe[F1, Byte, Byte]): SelfF[F1] =
    withBodyStream(pipe(body))

  // General header methods

  def transformHeaders(f: Headers => Headers): SelfF[F] =
    change(headers = f(headers))

  /** Keep headers that satisfy the predicate
    *
    * @param f predicate
    * @return a new message object which has only headers that satisfy the predicate
    */
  def filterHeaders(f: Header.Raw => Boolean): SelfF[F] =
    transformHeaders(_.transform(_.filter(f)))

  def removeHeader(key: CIString): SelfF[F] =
    transformHeaders(_.transform(_.filterNot(_.name == key)))

  def removeHeader[A](implicit h: Header[A, _]): SelfF[F] =
    removeHeader(h.name)

  /** Add the provided headers to the existing headers, replacing those
    * of the same header name
    *
    * {{{
    * >>> import org.http4s.headers.Accept
    *
    * >>> val req = Request().putHeaders(Accept(MediaRange.`application/*`))
    * >>> req.headers.get[Accept]
    * Some(Accept(NonEmptyList(application/*)))
    *
    * >>> val req2 = req.putHeaders(Accept(MediaRange.`text/*`))
    * >>> req2.headers.get[Accept]
    * Some(Accept(NonEmptyList(text/*)))
    * }}}
    * */*/*/*/
    */
  def putHeaders(headers: Header.ToRaw*): SelfF[F] =
    transformHeaders(_.put(headers: _*))

  /** Add a header to these headers.  The header should be a type with a
    * recurring `Header` instance to ensure that the new value can be
    * appended to any existing values.
    *
    * {{{
    * >>> import org.http4s.headers.Accept
    *
    * >>> val req = Request().addHeader(Accept(MediaRange.`application/*`))
    * >>> req.headers.get[Accept]
    * Some(Accept(NonEmptyList(application/*)))
    *
    * >>> val req2 = req.addHeader(Accept(MediaRange.`text/*`))
    * >>> req2.headers.get[Accept]
    * Some(Accept(NonEmptyList(application/*, text/*)))
    * }}}
    * */*/*/*/*/
    */
  def addHeader[H: Header[*, Header.Recurring]](h: H): SelfF[F] =
    transformHeaders(_.add(h))

  def withTrailerHeaders[F1[x] >: F[x]](trailerHeaders: F1[Headers]): SelfF[F1] =
    withAttribute(Message.Keys.TrailerHeaders[F1], trailerHeaders)

  def withoutTrailerHeaders: SelfF[F] =
    withoutAttribute(Message.Keys.TrailerHeaders[F])

  /** The trailer headers, as specified in Section 3.6.1 of RFC 2616. The resulting
    * F might not complete until the entire body has been consumed.
    */
  def trailerHeaders[F1[x] >: F[x]](implicit F: Applicative[F1]): F1[Headers] =
    attributes.lookup(Message.Keys.TrailerHeaders[F1]).getOrElse(Headers.empty.pure[F1])

  // Specific header methods

  def withContentType(contentType: `Content-Type`): SelfF[F] =
    putHeaders(contentType)

  def withoutContentType: SelfF[F] =
    removeHeader[`Content-Type`]

  def withContentTypeOption(contentTypeO: Option[`Content-Type`]): SelfF[F] =
    contentTypeO.fold[SelfF[F]](withoutContentType)(withContentType)

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
  def withAttribute[A](key: Key[A], value: A): SelfF[F] =
    change(attributes = attributes.insert(key, value))

  /** Returns a new message object without the specified key in the
    * [[attributes]].
    *
    * @param key [[org.typelevel.vault.Key]] to remove
    * @return a new message object without the key
    */
  def withoutAttribute(key: Key[_]): SelfF[F] =
    change(attributes = attributes.delete(key))

  /** Lifts this Message's body to the specified effect type.
    */
  override def covary[F2[x] >: F[x]]: SelfF[F2] = this.asInstanceOf[SelfF[F2]]

  def mapK[F2[x] >: F[x], G[_]](f: F2 ~> G): SelfF[G] =
    self.change(entity = entity.translate(f))

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
  def toStrict[F1[x] >: F[x]](
      maxBytes: Option[Long]
  )(implicit F: Concurrent[F1]): F1[SelfF[Pure]] = {
    def withLimit(entityBody: Stream[F, Byte]) = maxBytes match {
      case Some(limit) =>
        entityBody.pull
          .take(limit)
          .flatMap {
            case Some(_) =>
              Pull.raiseError[F1](EntityStreamException.createWithDefaultMsg(limit))
            case None =>
              Pull.done
          }
          .stream

      case None =>
        entityBody
    }

    entity match {
      case Entity.Empty =>
        F.pure(self.withEntity(Entity.Empty))
      case entity @ Entity.Strict(bytes) =>
        F.pure(
          self
            .withEntity(entity)
            .transformHeaders(
              _.withContentLength(`Content-Length`.unsafeFromLong(bytes.size))
            )
        )
      case Entity.Streamed(body, _) =>
        withLimit(body).covary[F1].compile.to(ByteVector).map { byteVector =>
          self
            .withEntity(Entity.strict(byteVector))
            .transformHeaders(
              _.withContentLength(`Content-Length`.unsafeFromLong(byteVector.size))
            )
        }
    }
  }
}

object Message {
  final case class EntityStreamException(msg: String) extends Exception(msg) with NoStackTrace

  object EntityStreamException {
    def createWithDefaultMsg(maxBytes: Long): EntityStreamException =
      EntityStreamException(s"Entity stream has exceeded the maximum of $maxBytes bytes")
  }

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
  * @param entity [[Entity]] defining the body of the request
  * @param attributes Immutable Map used for carrying additional information in a type safe fashion
  */
final class Request[+F[_]] private (
    val method: Method,
    val uri: Uri,
    val httpVersion: HttpVersion,
    val headers: Headers,
    val entity: Entity[F],
    val attributes: Vault,
) extends Message[F]
    with Product
    with Serializable {
  import Request._

  type SelfF[F0[_]] = Request[F0]

  private def copy[F1[_]](
      method: Method = this.method,
      uri: Uri = this.uri,
      httpVersion: HttpVersion = this.httpVersion,
      headers: Headers = this.headers,
      entity: Entity[F1] = this.entity,
      attributes: Vault = this.attributes,
  ): Request[F1] =
    Request(
      method = method,
      uri = uri,
      httpVersion = httpVersion,
      headers = headers,
      entity = entity,
      attributes = attributes,
    )

  def withMethod(method: Method): Request[F] =
    copy(method = method)

  def withUri(uri: Uri): Request[F] =
    copy(uri = uri, attributes = attributes.delete(Request.Keys.PathInfoCaret))

  override protected def change[F1[_]](
      httpVersion: HttpVersion,
      entity: Entity[F1],
      headers: Headers,
      attributes: Vault,
  ): Request[F1] =
    copy(
      httpVersion = httpVersion,
      entity = entity,
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

  /** Representation of the query string as a map
    *
    * In case a parameter is available in query string but no value is there the
    * list will be empty. If the value is empty the the list contains an
    * empty string.
    *
    * =====Examples=====
    * <table>
    * <tr><th>Query String</th><th>Map</th></tr>
    * <tr><td><code>?param=v</code></td><td><code>Map("param" -> List("v"))</code></td></tr>
    * <tr><td><code>?param=</code></td><td><code>Map("param" -> List(""))</code></td></tr>
    * <tr><td><code>?param</code></td><td><code>Map("param" -> List())</code></td></tr>
    * <tr><td><code>?=value</code></td><td><code>Map("" -> List("value"))</code></td></tr>
    * <tr><td><code>?p1=v1&amp;p1=v2&amp;p2=v3&amp;p2=v4</code></td><td><code>Map("p1" -> List("v1","v2"), "p2" -> List("v3","v4"))</code></td></tr>
    * </table>
    *
    * The query string is lazily parsed. If an error occurs during parsing
    * an empty `Map` is returned.
    */
  def multiParams: Map[String, List[String]] = uri.multiParams

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

  def remoteHost[F1[x] >: F[x]](implicit F: Monad[F1], dns: Dns[F1]): F1[Option[Hostname]] =
    remote match {
      case None => F.pure(None)
      case Some(sa) => dns.reverseOption(sa.host)
    }

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

  /** Checks whether the [[Entity]] of the [[Message]] is pure in the sense of
    * indicating that the entity body can be re-run without side effects.
    *
    * @return the `Boolean` value:
    *         'true' if entity is empty or already loaded into memory;
    *         'false' if entity is streamed.
    */
  def hasKnownPureEntity: Boolean =
    entity match {
      case Entity.Empty | Entity.Strict(_) =>
        true
      case Entity.Streamed(_, _) =>
        false
    }

  def decodeWith[F2[x] >: F[x], A](decoder: EntityDecoder[F2, A], strict: Boolean)(
      f: A => F2[Response[F2]]
  )(implicit F: Monad[F2]): F2[Response[F2]] =
    decoder
      .decode(this, strict = strict)
      .foldF(e => F.pure(e.toHttpResponse(httpVersion)), f)

  /** Helper method for decoding [[Request]]s
    *
    * Attempt to decode the [[Request]] and, if successful, execute the continuation to get a [[Response]].
    * If decoding fails, an `UnprocessableEntity` [[Response]] is generated.
    */
  def decode[F2[x] >: F[x], A](
      f: A => F2[Response[F2]]
  )(implicit F: Monad[F2], decoder: EntityDecoder[F2, A]): F2[Response[F2]] =
    decodeWith(decoder, strict = false)(f)

  /** Helper method for decoding [[Request]]s
    *
    * Attempt to decode the [[Request]] and, if successful, execute the continuation to get a [[Response]].
    * If decoding fails, an `UnprocessableEntity` [[Response]] is generated. If the decoder does not support the
    * [[MediaType]] of the [[Request]], a `UnsupportedMediaType` [[Response]] is generated instead.
    */
  def decodeStrict[F2[x] >: F[x], A](
      f: A => F2[Response[F2]]
  )(implicit F: Monad[F2], decoder: EntityDecoder[F2, A]): F2[Response[F2]] =
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
      case 4 => entity
      case 5 => attributes
      case _ => throw new IndexOutOfBoundsException()
    }

  /** A projection of this request without the body. */
  def requestPrelude: RequestPrelude =
    RequestPrelude.fromRequest(this)

  override def toString: String =
    s"""Request(method=$method, uri=$uri, httpVersion=${httpVersion}, headers=${headers
        .redactSensitive()}, entity=$entity)"""
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
    * @param entity [[Entity]] defining the body of the request
    * @param attributes Immutable Map used for carrying additional information in a type safe fashion
    */
  def apply[F[_]](
      method: Method = Method.GET,
      uri: Uri = Uri(path = Uri.Path.Root),
      httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
      headers: Headers = Headers.empty,
      entity: Entity[F] = Entity.empty,
      attributes: Vault = Vault.empty,
  ): Request[F] =
    new Request(
      method = method,
      uri = uri,
      httpVersion = httpVersion,
      headers = headers,
      entity = entity,
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

  implicit class RequestOps[F[_]](request: Request[F]) {

    /** cURL representation of the request and a new request
      *
      * Supported cURL-Parameters are: --request, --url, --header --data.
      * Note that `asCurlWith` will print the request body, which may have privacy implications.
      *
      * This method will consume the body create a new request with the cached body to avoid issues
      * this may cause, which may consume large amounts of memory to cache a request that streams a
      * large amount of data.
      */
    def asCurlWithBody(redactHeadersWhen: CIString => Boolean = Headers.SensitiveHeaders.contains)(
        implicit
        concurrent: Concurrent[F],
        defaultCharset: Charset = Charset.`UTF-8`,
    ): F[(String, Request[F])] =
      CurlConverter.requestToCurlWithBody(request, redactHeadersWhen)
  }
}

/** Representation of the HTTP response to send back to the client
  *
  * @param status [[Status]] code and message
  * @param headers [[Headers]] containing all response headers
  * @param entity [[Entity]] representing the possible body of the response
  * @param attributes [[org.typelevel.vault.Vault]] containing additional
  *                   parameters which may be used by the http4s backend for
  *                   additional processing such as java.io.File object
  */
final class Response[+F[_]] private (
    val status: Status,
    val httpVersion: HttpVersion,
    val headers: Headers,
    val entity: Entity[F],
    val attributes: Vault,
) extends Message[F]
    with Product
    with Serializable {
  type SelfF[F0[_]] = Response[F0]

  def withStatus(status: Status): Response[F] =
    copy(status = status)

  override protected def change[F1[_]](
      httpVersion: HttpVersion,
      entity: Entity[F1],
      headers: Headers,
      attributes: Vault,
  ): Response[F1] =
    copy(
      httpVersion = httpVersion,
      entity = entity,
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

  def copy[F1[_]](
      status: Status = this.status,
      httpVersion: HttpVersion = this.httpVersion,
      headers: Headers = this.headers,
      entity: Entity[F1] = this.entity,
      attributes: Vault = this.attributes,
  ): Response[F1] =
    Response(
      status = status,
      httpVersion = httpVersion,
      headers = headers,
      entity = entity,
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
      case 3 => entity
      case 4 => attributes
      case _ => throw new IndexOutOfBoundsException()
    }

  /** A projection of this response without the body. */
  def responsePrelude: ResponsePrelude =
    ResponsePrelude.fromResponse(this)

  override def toString: String =
    s"""Response(status=${status.code}, httpVersion=${httpVersion}, headers=${headers
        .redactSensitive()}, entity=$entity)"""
}

object Response extends KleisliSyntax {

  /** Representation of the HTTP response to send back to the client
    *
    * @param status [[Status]] code and message
    * @param headers [[Headers]] containing all response headers
    * @param entity [[Entity]] representing the possible body of the response
    * @param attributes [[org.typelevel.vault.Vault]] containing additional
    *                   parameters which may be used by the http4s backend for
    *                   additional processing such as java.io.File object
    */
  def apply[F[_]](
      status: Status = Status.Ok,
      httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
      headers: Headers = Headers.empty,
      entity: Entity[F] = Entity.empty,
      attributes: Vault = Vault.empty,
  ): Response[F] =
    new Response(status, httpVersion, headers, entity, attributes)

  def unapply[F[_]](
      response: Response[F]
  ): Option[(Status, HttpVersion, Headers, EntityBody[F], Vault)] =
    Some(
      (response.status, response.httpVersion, response.headers, response.body, response.attributes)
    )

  val notFound: Response[Pure] =
    Response(
      Status.NotFound,
      entity = Entity.utf8String("Not found"), // TODO
      headers = Headers(
        `Content-Type`(MediaType.text.plain, Charset.`UTF-8`),
        `Content-Length`.unsafeFromLong(9L),
      ),
    )

  def notFoundFor[F[_]: Applicative](request: Request[F])(implicit
      encoder: EntityEncoder[F, String]
  ): F[Response[F]] =
    Response(Status.NotFound).withEntity(s"${request.pathInfo} not found").pure[F]

  def timeout[F[_]]: Response[F] =
    Response(Status.ServiceUnavailable).withEntity("Response timed out")
}
