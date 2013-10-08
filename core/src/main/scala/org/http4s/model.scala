package org.http4s

import attributes.{RequestScope, Key}
import java.io.{InputStream, File}
import java.net.{URI, InetAddress}
import java.util.UUID
import scala.collection.{mutable, IndexedSeqOptimized}
import scala.collection.generic.CanBuildFrom
import scalaz.stream.Process
import scalaz.{RopeBuilder, Rope}
import java.nio.charset.Charset
import java.nio.ByteBuffer
import scala.io.Codec

// Our Http message "currency" types
sealed trait HasHeaders {
  def headers: HttpHeaders
}

sealed trait HttpPrelude extends HasHeaders

sealed trait HttpChunk extends IndexedSeq[Byte] {
  // TODO optimize for array reads
  def asInputStream: InputStream = new InputStream {
    var pos = 0

    def read(): Int = {
      val result = if (pos < length) apply(pos) else -1
      pos += 1
      result
    }
  }

  def decodeString(charset: HttpCharset): String = new String(toArray, charset.value)
}

class BodyChunk private (private val self: Rope[Byte]) extends HttpChunk with IndexedSeqOptimized[Byte, BodyChunk]
{
  override def apply(idx: Int): Byte = self.get(idx).getOrElse(throw new IndexOutOfBoundsException(idx.toString))

  def length: Int = self.length

  override protected[this] def newBuilder: mutable.Builder[Byte, BodyChunk] = BodyChunk.newBuilder
/*
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
*/

  def ++(b: BodyChunk): BodyChunk = BodyChunk(self ++ b.self)
}

object BodyChunk {
  type Builder = mutable.Builder[Byte, BodyChunk]

  def apply(rope: Rope[Byte]): BodyChunk = new BodyChunk(rope)

  def apply(bytes: Array[Byte]): BodyChunk = BodyChunk(Rope.fromArray(bytes))

  def apply(bytes: Byte*): BodyChunk = BodyChunk(bytes: _*)

  def apply(bytes: ByteBuffer): BodyChunk = BodyChunk(bytes.array)

  def apply(string: String): BodyChunk = apply(string, Codec.UTF8.charSet)

  def apply(string: String, charset: Charset): BodyChunk = BodyChunk(Rope.fromArray(string.getBytes(charset)))

  def fromArray(array: Array[Byte], offset: Int, length: Int): BodyChunk = BodyChunk(array.slice(offset, length))

  val empty: BodyChunk = BodyChunk(Rope.empty[Byte])

  private def newBuilder: Builder = (new RopeBuilder[Byte]).mapResult(BodyChunk.apply _)

  implicit def canBuildFrom: CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] =
    new CanBuildFrom[TraversableOnce[Byte], Byte, BodyChunk] {
      def apply(from: TraversableOnce[Byte]): Builder = newBuilder
      def apply(): Builder = newBuilder
    }
}

case class TrailerChunk(headers: HttpHeaders = HttpHeaders.empty) extends HttpChunk {
  override def apply(idx: Int): Byte = throw new IndexOutOfBoundsException(idx.toString)

  def length: Int = 0
}

object RequestPrelude {
//  def apply(
//    requestMethod: Method = Method.Get,
//    scriptName: String = "",
//    pathInfo: String = "",
//    queryString: String = "",
//    pathTranslated: Option[File] = None,
//    protocol: ServerProtocol = HttpVersion.`Http/1.1`,
//    headers: HttpHeaders = HttpHeaders.empty,
//    cookies: RequestCookieJar = RequestCookieJar.empty,
//    urlScheme: UrlScheme = HttpUrlScheme.Http,
//    serverName: String = InetAddress.getLocalHost.getHostName,
//    serverPort: Int = 80,
//    serverSoftware: ServerSoftware = ServerSoftware.Unknown,
//    remote: InetAddress = InetAddress.getLocalHost): RequestPrelude =
//    new RequestPrelude(
//      requestMethod,
//      scriptName,
//      pathInfo,
//      queryString,
//      pathTranslated,
//      protocol,
//      headers,
//      urlScheme,
//      serverName,
//      serverPort,
//      serverSoftware,
//      remote
//    )
//
//  def unapply(request: RequestPrelude): Option[(Method, String, String, String, Option[File], ServerProtocol, HttpHeaders, UrlScheme, String, Int, ServerSoftware, HttpIp)] =
//    Some((request.requestMethod, request.scriptName, request.pathInfo, request.queryString, request.pathTranslated, request.protocol, request.headers, request.urlScheme, request.serverName, request.serverPort, request.serverSoftware, request.remote))
def apply(requestMethod: Method = Method.Get,
         scriptName: String = "",
         pathInfo: String = "",
         queryString: String = "",
         pathTranslated: Option[File] = None,
         protocol: ServerProtocol = HttpVersion.`Http/1.1`,
         headers: HttpHeaders = HttpHeaders.empty,
         urlScheme: UrlScheme = HttpUrlScheme.Http,
         serverName: String = InetAddress.getLocalHost.getHostName,
         serverPort: Int = 80,
         serverSoftware: ServerSoftware = ServerSoftware.Unknown,
         remote: HttpIp = HttpIp.localhost) =
    new RequestPrelude(
      requestMethod,
      scriptName,
      pathInfo,
      queryString,
      pathTranslated,
      protocol: ServerProtocol,
      headers: HttpHeaders,
      urlScheme: UrlScheme,
      serverName: String,
      serverPort: Int,
      serverSoftware,
      remote: HttpIp,
      new RequestScope(),
      UUID.randomUUID())

  implicit def reqToScope(req: RequestPrelude) = req.scope

  private def cookiesFromHeaders(h: HttpHeaders) = h.getAll(HttpHeaders.Cookie).flatMap(_.cookies).distinct
}
case class RequestPrelude private(
      requestMethod: Method,
      scriptName: String,
      pathInfo: String,
      queryString: String,
      pathTranslated: Option[File],
      protocol: ServerProtocol,
      headers: HttpHeaders,
      urlScheme: UrlScheme,
      serverName: String,
      serverPort: Int,
      serverSoftware: ServerSoftware,
      remote: HttpIp,
      scope: RequestScope,
      uuid: UUID) {

  def this(requestMethod: Method = Method.Get,
    scriptName: String = "",
    pathInfo: String = "",
    queryString: String = "",
    pathTranslated: Option[File] = None,
    protocol: ServerProtocol = HttpVersion.`Http/1.1`,
    headers: HttpHeaders = HttpHeaders.empty,
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
      protocol: ServerProtocol,
      headers: HttpHeaders,
      urlScheme: UrlScheme,
      serverName: String,
      serverPort: Int,
      serverSoftware,
      remote: HttpIp,
      new RequestScope(),
      UUID.randomUUID())

  def contentLength: Option[Int] = headers.get(HttpHeaders.ContentLength).map(_.length)

  def contentType: Option[ContentType] = headers.get(HttpHeaders.ContentType).map(_.contentType)

  def charset: HttpCharset = contentType.map(_.charset) getOrElse HttpCharsets.`ISO-8859-1`

  val uri: URI = new URI(urlScheme.toString, null, serverName, serverPort, scriptName+pathInfo, queryString, null)

  lazy val authType: Option[AuthType] = None

  lazy val remoteAddr = remote.hostAddress
  lazy val remoteHost = remote.hostName

  lazy val remoteUser: Option[String] = None

  /* Attributes proxy */

  def updated[T](key: Key[T], value: T) = { scope.updated(key, value); this }

  def update[T](key: Key[T], value: T) = scope.update(key, value)

  def apply[T](key: Key[T]): T = scope(key)
  def get[T](key: Key[T]): Option[T] = scope.get(key)
  def getOrElse[T](key: Key[T], default: => T) = get(key).getOrElse(default)
  def +[T](kv: (Key[T], T)) = {
    scope(kv._1) = kv._2
    this
  }

  def -[T](key: Key[T]) = { scope.remove(key); this }

  def contains[T](key: Key[T]): Boolean = scope.contains(key)
  /* Attributes proxy end */

 override def hashCode(): Int = uuid.##

  override def equals(obj: Any): Boolean = obj match {
    case req: RequestPrelude => req.uuid == uuid
    case _ => false
  }

  override def toString: String = {
    s"RequestPrelude(uuid: $uuid, method: $requestMethod, pathInfo: $pathInfo, queryString: $queryString, " +
    s"pathTranslated: $pathTranslated, protocol: $protocol, urlScheme: $urlScheme, serverName: $serverName, " +
    s"serverPort: $serverPort, serverSoftware: $serverSoftware, remote: $remote, headers: $headers, attributes: ${scope.toMap}"
  }

  override def clone(): AnyRef = copy()
}

case class Request(prelude: RequestPrelude = RequestPrelude(), body: HttpBody = Process.halt)

case class ResponsePrelude(status: Status = Status.Ok, headers: HttpHeaders = HttpHeaders.empty) extends HttpPrelude
