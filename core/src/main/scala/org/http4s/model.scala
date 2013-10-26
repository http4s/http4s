package org.http4s

import java.io.File
import java.net.{URI, InetAddress}
import akka.util.{ByteIterator, ByteStringBuilder, ByteString}
import collection.{mutable, IndexedSeqOptimized}
import scala.collection.generic.CanBuildFrom
import java.nio.ByteBuffer
import org.http4s.util.{AttributeKey, AttributeMap}

// Our Http message "currency" types
sealed trait HasHeaders {
  def headers: HttpHeaders
}

sealed trait HttpPrelude extends HasHeaders

// IPC: Do we still need HttpChunk?
sealed trait HttpChunk

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

  def apply(bytes: ByteBuffer): BodyChunk = BodyChunk(ByteString(bytes))

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

case class TrailerChunk(headers: HttpHeaders = HttpHeaders.empty) extends HttpChunk {
  final def bytes: ByteString = ByteString.empty
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
  remote: HttpIp = HttpIp.localhost,
  attributes: AttributeMap = AttributeMap.empty
) {
  def contentLength: Option[Int] = headers.get(HttpHeaders.ContentLength).map(_.length)

  def contentType: Option[ContentType] = headers.get(HttpHeaders.ContentType).map(_.contentType)

  def charset: HttpCharset = contentType.map(_.charset) getOrElse HttpCharsets.`ISO-8859-1`

  val uri: URI = new URI(urlScheme.toString, null, serverName, serverPort, scriptName+pathInfo, queryString, null)

  lazy val authType: Option[AuthType] = None

  lazy val remoteAddr = remote.hostAddress
  lazy val remoteHost = remote.hostName

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
case class ResponsePrelude(status: Status, headers: HttpHeaders = HttpHeaders.empty) extends HttpPrelude
