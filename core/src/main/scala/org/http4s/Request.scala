package org.http4s

import java.io.File
import java.net.{URI, InetAddress}
import play.api.libs.iteratee.{Enumeratee, Enumerator}
import java.nio.charset.Charset
import java.util.UUID
import scala.language.reflectiveCalls

//case class RequestHead(
//  requestMethod: Method = Method.Get,
//  scriptName: String = "",
//  pathInfo: String = "",
//  queryString: String = "",
//  pathTranslated: Option[File] = None,
//  protocol: ServerProtocol = HttpVersion.`Http/1.1`,
//  headers: Headers = Headers.Empty,
//  urlScheme: UrlScheme = UrlScheme.Http,
//  serverName: String = InetAddress.getLocalHost.getHostName,
//  serverPort: Int = 80,
//  serverSoftware: ServerSoftware = ServerSoftware.Unknown,
//  remote: InetAddress = InetAddress.getLocalHost,
//  http4sVersion: Http4sVersion = Http4sVersion,
//  private val requestId: UUID = UUID.randomUUID()
//) {
//
//  lazy val contentLength: Option[Long] = headers.get("Content-Length").map(_.value.toLong)
//
//  lazy val contentType: Option[ContentType] = headers.get("Content-Type").map(_.asInstanceOf[ContentType])
//
//  lazy val charset: Charset = contentType.map(_.charset.nioCharset) getOrElse Charset.defaultCharset()
//
//  lazy val uri: URI = new URI(urlScheme.toString, null, serverName, serverPort, scriptName+pathInfo, queryString, null)
//
//  lazy val authType: Option[AuthType] = ???
//
//  lazy val remoteAddr = remote.getHostAddress
//  lazy val remoteHost = remote.getHostName
//
//  lazy val remoteUser: Option[String] = None
//}
