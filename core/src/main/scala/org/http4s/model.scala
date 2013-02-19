package org.http4s

import java.io.File
import java.net.{URI, InetAddress}
import java.util.UUID
import java.nio.charset.Charset
import org.http4s.HttpHeaders.Host

// Our Http message "currency" types
sealed trait HasHeaders {
  def headers: Headers
}

sealed trait HttpPrelude extends HasHeaders

sealed trait HttpChunk {
  def bytes: Raw
}

sealed trait HttpBodyChunk extends HttpChunk
case class HttpEntity(bytes: Raw) extends HttpBodyChunk

sealed trait MultipartEntity extends HttpBodyChunk {
  def name: String
  def contentType: String
}
case class MultipartChunk(bytes: Raw, contentType: String, name: String) extends MultipartEntity
case class FileChunk(bytes: Raw, contentType: String, name: String) extends MultipartEntity

case class RequestPrelude(
  requestMethod: Method = Method.Get,
  uri: URI = new URI("http://localhost/"),
  pathInfo: String = "", // must be suffix of uri.getPath
  pathTranslated: Option[File] = None,
  protocol: ServerProtocol = HttpVersion.`Http/1.1`,
  headers: Headers = Headers.Empty,
  serverSoftware: ServerSoftware = ServerSoftware.Unknown,
  remote: InetAddress = InetAddress.getLocalHost,
  http4sVersion: Http4sVersion = Http4sVersion,
  private val requestId: UUID = UUID.randomUUID()) extends HttpPrelude {

  lazy val contentLength: Option[Long] = headers.get("Content-Length").map(_.value.toLong)

  lazy val contentType: Option[ContentType] = headers.get("Content-Type").map(_.asInstanceOf[ContentType])

  lazy val charset: Charset = contentType.map(_.charset.nioCharset) getOrElse Charset.defaultCharset()

  lazy val authType: Option[AuthType] = ???

  lazy val urlScheme: UrlScheme = UrlScheme.apply(uri.getScheme)
  lazy val serverName: String = uri.getHost
  lazy val serverPort: Int = if (uri.getPort >= 0) uri.getPort else 80
  lazy val scriptName = uri.getPath.substring(0, uri.getPath.length - pathInfo.length)
  lazy val queryString = Option(uri.getQuery).getOrElse("")

  lazy val remoteAddr = remote.getHostAddress
  lazy val remoteHost = remote.getHostName

  lazy val remoteUser: Option[String] = None
}
case class ResponsePrelude(status: Status, headers: Headers = Headers.empty) extends HttpPrelude
case class HttpTrailer(headers: Headers) extends HasHeaders with HttpChunk {
  final val bytes = Array.empty[Byte]
}