package org.http4s

import java.io.File
import java.net.{URI, URL, InetAddress}

case class Request(
  val requestMethod: Method = Method.Get,
  val scriptName: String = "",
  val pathInfo: String = "",
  val queryString: String = "",
  val pathTranslated: Option[File] = None,
  val protocol: ServerProtocol = HttpVersion.Http_1_1,
  val headers: RequestHeaders,
  val urlScheme: UrlScheme = UrlScheme.Http,
  val serverName: String = InetAddress.getLocalHost.getHostName,
  val serverPort: Int = 80,
  val serverSoftware: Option[ServerSoftware] = None,
  val remote: InetAddress = InetAddress.getLocalHost,
  val authType: Option[AuthType] = None,
  val http4sVersion: Http4sVersion = Http4sVersion
) {
  def contentLength: Option[Long] = headers.contentLength
  def contentType: Option[ContentType] = headers.contentType
  lazy val uri: URI = new URI(urlScheme.toString, null, serverName, serverPort, scriptName+pathInfo, queryString, null)
}