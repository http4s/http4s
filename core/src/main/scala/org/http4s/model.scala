package org.http4s

import java.io.File
import java.net.{URI, InetAddress}
import java.nio.charset.Charset
import java.util.UUID
import akka.util.{ByteIterator, ByteStringBuilder, ByteString}
import collection.{mutable, IndexedSeqLike, IndexedSeqOptimized}
import scala.collection.generic.CanBuildFrom
import java.nio.ByteBuffer
import io.Codec

// Our Http message "currency" types
sealed trait HasHeaders {
  def headers: Headers
}

sealed trait HttpPrelude extends HasHeaders

sealed trait HttpChunk {
}

case class BodyChunk(bytes: ByteString) extends HttpChunk
  with IndexedSeq[Byte] with IndexedSeqOptimized[Byte, BodyChunk]
{
  override def apply(idx: Int): Byte = bytes(idx)

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
  final def utf8String: String = decodeString(Codec.UTF8.charSet)

  /**
   * Decodes this ByteString using a charset to produce a String.
   */
  def decodeString(charset: Charset): String = bytes.decodeString(charset.name)
}

object BodyChunk {
  type Builder = mutable.Builder[Byte, BodyChunk]

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

case class RequestPrelude(
  requestMethod: Method = Method.Get,
  scriptName: String = "",
  pathInfo: String = "",
  queryString: String = "",
  pathTranslated: Option[File] = None,
  protocol: ServerProtocol = HttpVersion.`Http/1.1`,
  headers: Headers = Headers.Empty,
  urlScheme: UrlScheme = UrlScheme.Http,
  serverName: String = InetAddress.getLocalHost.getHostName,
  serverPort: Int = 80,
  serverSoftware: ServerSoftware = ServerSoftware.Unknown,
  remote: InetAddress = InetAddress.getLocalHost,
  http4sVersion: Http4sVersion = Http4sVersion,
  private val requestId: UUID = UUID.randomUUID()) extends HttpPrelude {

  lazy val contentLength: Option[Long] = headers.get("Content-Length").map(_.value.toLong)

  lazy val contentType: Option[ContentType] = headers.get("Content-Type").map(_.asInstanceOf[ContentType])

  lazy val charset: Charset = contentType.map(_.charset.nioCharset) getOrElse Charset.defaultCharset()

  lazy val uri: URI = new URI(urlScheme.toString, null, serverName, serverPort, scriptName+pathInfo, queryString, null)

  lazy val authType: Option[AuthType] = ???

  lazy val remoteAddr = remote.getHostAddress
  lazy val remoteHost = remote.getHostName

  lazy val remoteUser: Option[String] = None
}
case class ResponsePrelude(status: Status, headers: Headers = Headers.empty) extends HttpPrelude
