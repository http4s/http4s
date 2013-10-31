package org.http4s

import java.io.InputStream
import java.io.File
import java.net.{URI, InetAddress}
import collection.{mutable, IndexedSeqOptimized}
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

case class RequestPrelude(
  requestMethod: Method = Method.Get,
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
  remote: InetAddress = InetAddress.getLocalHost,
  attributes: AttributeMap = AttributeMap.empty
) {
  def contentLength: Option[Int] = headers.get(HttpHeaders.ContentLength).map(_.length)

  def contentType: Option[ContentType] = headers.get(HttpHeaders.ContentType).map(_.contentType)

  def charset: HttpCharset = contentType.map(_.charset) getOrElse HttpCharsets.`ISO-8859-1`

  val uri: URI = new URI(urlScheme.toString, null, serverName, serverPort, scriptName+pathInfo, queryString, null)

  lazy val authType: Option[AuthType] = None

  lazy val remoteAddr = remote.getHostAddress
  lazy val remoteHost = remote.getHostName

  lazy val remoteUser: Option[String] = None

  /* Attributes proxy */
  def updated[T](key: AttributeKey[T], value: T) = copy(attributes = attributes.put(key, value))
  def apply[T](key: AttributeKey[T]): T = attributes(key)
  def get[T](key: AttributeKey[T]): Option[T] = attributes.get(key)
  def getOrElse[T](key: AttributeKey[T], default: => T) = get(key).getOrElse(default)
  def +[T](kv: (AttributeKey[T], T)) = updated(kv._1, kv._2)
  def -[T](key: AttributeKey[T]) = copy(attributes = attributes.remove(key))
  def contains[T](key: AttributeKey[T]): Boolean = attributes.contains(key)
}

case class Request(prelude: RequestPrelude = RequestPrelude(), body: HttpBody = Process.halt)

case class ResponsePrelude(status: Status = Status.Ok, headers: HttpHeaders = HttpHeaders.empty) extends HttpPrelude
