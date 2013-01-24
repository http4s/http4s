package org.http4s

import java.io.File
import java.net.{URL, InetAddress}

trait Request {
  def authType: Option[AuthType]

  def contentLength: Option[Long] = headers.contentLength

  def contentType: Option[ContentType] = headers.contentType

  def http4sVersion: Http4sVersion = Http4sVersion

  def pathInfo: String = url.getPath

  def pathTranslated: Option[File] = None

  def queryString: String = Option(url.getQuery).getOrElse("")

  def remoteAddr: InetAddress

  def remoteHost: String

  def remoteIdent: Option[String]

  def remoteUser: Option[String]

  def requestMethod: Method

  def scriptName: String = ""

  def serverName: String = url.getHost

  def serverPort: Int = url.getPort

  def serverProtocol: ServerProtocol

  def serverSoftware: ServerSoftware

  def headers: RequestHeaders

  def urlScheme: UrlScheme = url.getProtocol match {
    case "http" => UrlScheme.Http
    case "https" => UrlScheme.Https
  }

  def url: URL = url
}
