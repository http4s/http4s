package org.http4s

import java.io.File
import java.net.InetAddress

import play.api.libs.iteratee.Enumerator

trait Request {
  def authType: Option[AuthType]

  def contentLength: Option[Long] = headers.contentLength

  def contentType: Option[ContentType] = headers.contentType

  def http4sVersion: Http4sVersion = Http4sVersion

  def pathInfo: String

  def pathTranslated: Option[File] = None

  def queryString: String

  def remoteAddr: InetAddress

  def remoteHost: String

  def remoteIdent: Option[String]

  def remoteUser: Option[String]

  def requestMethod: Method

  def scriptName: String

  def serverName: InetAddress

  def serverPort: Short

  def serverProtocol: ServerProtocol

  def serverSoftware: ServerSoftware

  def headers: RequestHeaders

  def entityBody: Enumerator[Array[Byte]] = Enumerator.eof
}
