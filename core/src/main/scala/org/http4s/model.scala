package org.http4s

import attributes.{AttributesView, RequestScope, AttributeKey}
import java.io.File
import java.net.{URI, InetAddress}
import java.util.UUID
import akka.util.{ByteIterator, ByteStringBuilder, ByteString}
import collection.{mutable, IndexedSeqOptimized}
import scala.collection.generic.CanBuildFrom
import java.nio.ByteBuffer
import io.Codec

// Our Http message "currency" types
sealed trait HasHeaders {
  def headers: Headers
}

sealed trait HttpPrelude extends HasHeaders

// IPC: Do we still need HttpChunk?
sealed trait HttpChunk {
}

case class BodyChunk(bytes: ByteString) extends HttpChunk
  with IndexedSeq[Byte] with IndexedSeqOptimized[Byte, BodyChunk]
{
  override def apply(idx: Int): Byte = bytes(idx)

  override def toArray[B >: Byte](implicit evidence$1: scala.reflect.ClassTag[B]): Array[B] = bytes.toArray

  def length: Int = bytes.length

  override protected[this] def newBuilder: mutable.Builder[Byte, BodyChunk] = BodyChunk.newBuilder

  override def iterator: ByteIterator = bytes.iterator

  /**
   * Returns a read-only ByteBuffer that directly wraps this ByteString
   * if it is not fragmented.
   */
  def asByteBuffer: ByteBuffer = bytes.asByteBuffer

  /**
   * Creates a new ByteBuffer with a copy of all bytes contained in this
   * ByteString.
   */
  def toByteBuffer: ByteBuffer = bytes.toByteBuffer

  /**
   * Decodes this ByteString as a UTF-8 encoded String.
   */
  final def utf8String: String = decodeString(HttpCharsets.`UTF-8`)

  /**
   * Decodes this ByteString using a charset to produce a String.
   */
  def decodeString(charset: HttpCharset): String = bytes.decodeString(charset.value)
}

object BodyChunk {
  type Builder = mutable.Builder[Byte, BodyChunk]

  def apply(bytes: Array[Byte]): BodyChunk = BodyChunk(ByteString(bytes))

  def apply(bytes: Byte*): BodyChunk = BodyChunk(ByteString(bytes: _*))

  def apply[T](bytes: T*)(implicit num: Integral[T]): BodyChunk = BodyChunk(ByteString(bytes: _*)(num))

  def apply(bytes: ByteBuffer): BodyChunk = BodyChunk(bytes)

  def apply(string: String): BodyChunk = apply(string, HttpCharsets.`UTF-8`)

  def apply(string: String, charset: HttpCharset): BodyChunk = BodyChunk(ByteString(string, charset.value))

  def fromArray(array: Array[Byte], offset: Int, length: Int): BodyChunk =
    BodyChunk(ByteString.fromArray(array, offset, length))

  val Empty: BodyChunk = BodyChunk(ByteString.empty)

  private def newBuilder: Builder = (new ByteStringBuilder).mapResult(BodyChunk(_))

  implicit def canBuildFrom: CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] =
    new CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] {
      def apply(from: TraversableOnce[Byte]): Builder = newBuilder
      def apply(): Builder = newBuilder
    }
}

case class TrailerChunk(headers: Headers = Headers.empty) extends HttpChunk {
  final def bytes: ByteString = ByteString.empty
}

object RequestPrelude {
  def apply(
    requestMethod: Method = Method.Get,
    scriptName: String = "",
    pathInfo: String = "",
    queryString: String = "",
    pathTranslated: Option[File] = None,
    protocol: ServerProtocol = HttpVersion.`Http/1.1`,
    headers: Headers = Headers.empty,
    cookies: RequestCookieJar = RequestCookieJar.empty,
    urlScheme: UrlScheme = HttpUrlScheme.Http,
    serverName: String = InetAddress.getLocalHost.getHostName,
    serverPort: Int = 80,
    serverSoftware: ServerSoftware = ServerSoftware.Unknown,
    remote: InetAddress = InetAddress.getLocalHost): RequestPrelude =
    new RequestPrelude(
      requestMethod,
      scriptName,
      pathInfo,
      queryString,
      pathTranslated,
      protocol,
      headers,
      urlScheme,
      serverName,
      serverPort,
      serverSoftware,
      remote
    )

  def unapply(request: RequestPrelude): Option[(Method, String, String, String, Option[File], ServerProtocol, Headers, UrlScheme, String, Int, ServerSoftware, HttpIp)] =
    Some((request.requestMethod, request.scriptName, request.pathInfo, request.queryString, request.pathTranslated, request.protocol, request.headers, request.urlScheme, request.serverName, request.serverPort, request.serverSoftware, request.remote))

  private def cookiesFromHeaders(h: Headers) =
      h.getAll("Cookie").collect({case c: HttpHeaders.Cookie => c.cookies}).flatten.distinct
}
final class RequestPrelude private(
  val requestMethod: Method,
  val scriptName: String,
  val pathInfo: String,
  val queryString: String,
  val pathTranslated: Option[File],
  val protocol: ServerProtocol,
  val headers: Headers,
  val cookies: RequestCookieJar,
  val urlScheme: UrlScheme,
  val serverName: String,
  val serverPort: Int,
  val serverSoftware: ServerSoftware,
  val remote: HttpIp,
  scope: RequestScope) extends HttpPrelude {

  def this(
      requestMethod: Method = Method.Get,
      scriptName: String = "",
      pathInfo: String = "",
      queryString: String = "",
      pathTranslated: Option[File] = None,
      protocol: ServerProtocol = HttpVersion.`Http/1.1`,
      headers: Headers = Headers.empty,
      urlScheme: UrlScheme = HttpUrlScheme.Http,
      serverName: String = InetAddress.getLocalHost.getHostName,
      serverPort: Int = 80,
      serverSoftware: ServerSoftware = ServerSoftware.Unknown,
      remote: HttpIp = HttpIp.localhost) =
      this(
        requestMethod,
        scriptName,
        pathInfo,
        queryString,
        pathTranslated,
        protocol,
        headers,
        RequestCookieJar(RequestPrelude.cookiesFromHeaders(headers):_*),
        urlScheme,
        serverName,
        serverPort,
        serverSoftware,
        remote,
        RequestScope(UUID.randomUUID()))
  private[this] implicit val _scope = scope



  def uuid = scope.uuid
  lazy val contentLength: Option[Int] = headers.get("Content-Length").collectFirst({case c: HttpHeaders.`Content-Length` => c.length})

  lazy val contentType: Option[ContentType] = headers.get("Content-Type").collectFirst({case c: HttpHeaders.`Content-Type` => c.contentType })

  lazy val charset: HttpCharset = contentType.map(_.charset) getOrElse HttpCharsets.`ISO-8859-1`

  lazy val uri: URI = new URI(urlScheme.toString, null, serverName, serverPort, scriptName+pathInfo, queryString, null)

  lazy val authType: Option[AuthType] = None

  lazy val remoteAddr = remote.hostAddress
  lazy val remoteHost = remote.hostName

  lazy val remoteUser: Option[String] = None

  val attributes = new AttributesView(GlobalState.forScope(scope))

  /* Attributes proxy */
  def updated[T](key: AttributeKey[T], value: T) = {
    attributes.updated(key, value)
    this
  }
  def apply[T](key: AttributeKey[T]): T = attributes(key)
  def get[T](key: AttributeKey[T]): Option[T] = attributes get key
  def getOrElse[T](key: AttributeKey[T], default: => T) = attributes.getOrElse(key, default)
  def +[T](kv: (AttributeKey[T], T)) = {
    attributes += kv
    this
  }

  def -[T](key: AttributeKey[T]) = {
    attributes -= key
    this
  }
  def contains[T](key: AttributeKey[T]): Boolean = attributes contains key
  /* Attributes proxy end */

  def copy(
      requestMethod: Method = requestMethod,
      scriptName: String = scriptName,
      pathInfo: String = pathInfo,
      queryString: String = queryString,
      pathTranslated: Option[File] = pathTranslated,
      protocol: ServerProtocol = protocol,
      headers: Headers = headers,
      urlScheme: UrlScheme = urlScheme,
      serverName: String = serverName,
      serverPort: Int = serverPort,
      serverSoftware: ServerSoftware =serverSoftware,
      remote: HttpIp = remote): RequestPrelude =
      new RequestPrelude(
        requestMethod,
        scriptName,
        pathInfo,
        queryString,
        pathTranslated,
        protocol,
        headers,
        if (headers != this.headers) RequestCookieJar(RequestPrelude.cookiesFromHeaders(headers):_*) else cookies,
        urlScheme,
        serverName,
        serverPort,
        serverSoftware,
        remote,
        scope)

  override def hashCode(): Int = scope.uuid.##

  override def equals(obj: Any): Boolean = obj match {
    case req: RequestPrelude => req.uuid == uuid
    case _ => false
  }

  override def toString: String = {
    s"RequestPrelude(uuid: $uuid, method: $requestMethod, pathInfo: $pathInfo, queryString: $queryString, " +
    s"pathTranslated: $pathTranslated, protocol: $protocol, urlScheme: $urlScheme, serverName: $serverName, " +
    s"serverPort: $serverPort, serverSoftware: $serverSoftware, remote: $remote, headers: $headers, attributes: ${attributes.toMap}"
  }

  override def clone(): AnyRef = copy()
}
case class ResponsePrelude(status: Status, headers: Headers = Headers.empty) extends HttpPrelude
