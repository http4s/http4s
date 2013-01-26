package org.http4s

import java.io.File
import java.net.{URI, URL, InetAddress}

case class Request(
  requestMethod: Method = Method.Get,
  scriptName: String = "",
  pathInfo: String = "",
  queryString: String = "",
  pathTranslated: Option[File] = None,
  protocol: ServerProtocol = HttpVersion.Http_1_1,
  headers: RequestHeaders,
  urlScheme: UrlScheme = UrlScheme.Http,
  serverName: String = InetAddress.getLocalHost.getHostName,
  serverPort: Int = 80,
  serverSoftware: Option[ServerSoftware] = None,
  remote: InetAddress = InetAddress.getLocalHost,
  http4sVersion: Http4sVersion = Http4sVersion
) {
  def contentLength: Option[Long] = headers.contentLength
  def contentType: Option[ContentType] = headers.contentType
  lazy val uri: URI = new URI(urlScheme.toString, null, serverName, serverPort, scriptName+pathInfo, queryString, null)
  lazy val authType: Option[AuthType] = None
  lazy val remoteAddr = remote.getHostAddress
  lazy val remoteHost = remote.getHostName
}

trait RequestHeaders extends Headers

object RequestHeaders {
  val Empty: RequestHeaders = ???
}
