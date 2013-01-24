package org.http4s

import java.io.File
import java.net.InetAddress

import play.api.libs.iteratee.Enumerator

trait Request {
  def authType: Option[AuthType] = None

  def contentLength: Option[Long] = None

  def contentType: Option[ContentType] = None

  def http4sVersion: Http4sVersion = Http4sVersion

  def pathInfo: String = "/"

  def pathTranslated: Option[File] = None

  def queryString: String = ""

  def remoteAddr: InetAddress = Request.Localhost

  def remoteHost: String = remoteAddr.toString

  def remoteIdent: Option[String] = None

  def remoteUser: Option[String] = None

  def requestMethod: HttpMethod = HttpMethod.Get

  def scriptName: String = ""

  def serverName: InetAddress = Request.Localhost

  def serverPort: Short = 80

  def serverProtocol: ServerProtocol = HttpVersion.Http_1_1

  def serverSoftware: ServerSoftware

  def headers: RequestHeaders = RequestHeaders.Empty

  def entityBody: Enumerator[Array[Byte]] = Enumerator.eof
}

object Request {
  private val Localhost = InetAddress.getLocalHost
}
