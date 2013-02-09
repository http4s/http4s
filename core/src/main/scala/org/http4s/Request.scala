package org.http4s

import java.io.File
import java.net.{URI, URL, InetAddress}
import play.api.libs.iteratee.{Input, Iteratee, Enumerator}
import java.nio.charset.Charset

case class Request(
  requestMethod: Method = Method.Get,
  scriptName: String = "",
  pathInfo: String = "",
  queryString: String = "",
  pathTranslated: Option[File] = None,
  protocol: ServerProtocol = HttpVersion.Http_1_1,
  headers: Headers = Headers.Empty,
  body: (Iteratee[Chunk, Unit] => Any) = _.feed(Input.EOF),
  urlScheme: UrlScheme = UrlScheme.Http,
  serverName: String = InetAddress.getLocalHost.getHostName,
  serverPort: Int = 80,
  serverSoftware: ServerSoftware = ServerSoftware.Unknown,
  remote: InetAddress = InetAddress.getLocalHost,
  http4sVersion: Http4sVersion = Http4sVersion
) {
  lazy val contentLength: Option[Long] = headers.get("Content-Length").map(_.toLong)
  lazy val contentType: Option[ContentType] = headers.get("Content-Type").map(???)
  lazy val charset: Charset = Charset.defaultCharset() // TODO get from content type
  lazy val uri: URI = new URI(urlScheme.toString, null, serverName, serverPort, scriptName+pathInfo, queryString, null)
  lazy val authType: Option[AuthType] = ???
  lazy val remoteAddr = remote.getHostAddress
  lazy val remoteHost = remote.getHostName
  lazy val remoteUser: Option[String] = ???
}
