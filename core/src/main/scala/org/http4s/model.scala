package org.http4s

import java.io.File
import java.net.{URI, InetAddress}
import java.util.UUID
import java.nio.charset.Charset
import akka.util.ByteString

// Our Http message "currency" types
sealed trait HasHeaders {
  def headers: Headers
}

sealed trait HttpPrelude extends HasHeaders

sealed trait HttpChunk {
  def bytes: ByteString
}

case class BodyChunk(bytes: ByteString) extends HttpChunk

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

case class TrailerChunk(headers: Headers) extends HasHeaders with HttpChunk {
  final val bytes = ByteString.empty
}