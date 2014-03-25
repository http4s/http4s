package org.http4s

import scalaz.stream.Process
import java.io.File
import java.net.{URI, InetAddress}
import org.http4s.util.CaseInsensitiveString
import scalaz.concurrent.Task
import org.http4s.Header.`Content-Type`

abstract class Message(headers: HeaderCollection, body: HttpBody, attributes: AttributeMap) {
  type Self <: Message

  def withHeaders(headers: HeaderCollection): Self

  def contentLength: Option[Int] = headers.get(Header.`Content-Length`).map(_.length)

  def contentType: Option[`Content-Type`] = headers.get(Header.`Content-Type`)
  def withContentType(contentType: Option[`Content-Type`]): Self =
    withHeaders(contentType match {
      case Some(ct) => headers.put(ct)
      case None => headers.filterNot(_.is(Header.`Content-Type`))
    })

  def charset: CharacterSet = contentType.map(_.charset) getOrElse CharacterSet.`ISO-8859-1`

  def withBody(body: HttpBody): Self

  def addHeader(header: Header): Self = withHeaders(headers = header +: headers)

  def putHeader(header: Header): Self = withHeaders(headers = headers.put(header))

  def filterHeaders(f: Header => Boolean): Self =
    withHeaders(headers = headers.filter(f))

  def removeHeader(key: HeaderKey): Self = filterHeaders(_ isNot key)

  def isChunked: Boolean = headers.get(Header.`Transfer-Encoding`)
    .map(_.values.list.contains(TransferCoding.chunked))
    .getOrElse(false)
}

case class Request(
  requestMethod: Method = Method.Get,
  requestUri: Uri = Uri(path = "/"),
  protocol: ServerProtocol = ServerProtocol.`HTTP/1.1`,
  headers: HeaderCollection = HeaderCollection.empty,
  body: HttpBody = HttpBody.empty,
  attributes: AttributeMap = AttributeMap.empty
) extends Message(headers, body, attributes) {
  import Request._

  type Self = Request
  def withHeaders(headers: HeaderCollection): Request = copy(headers = headers)
  def withBody(body: HttpBody): Request = copy(body = body)
  def withAttribute[T](key: AttributeKey[T], value: T) = copy(attributes = attributes.put(key, value))

  lazy val authType: Option[AuthScheme] = headers.get(Header.Authorization).map(_.credentials.authScheme)

  lazy val (scriptName, pathInfo) = {
    val caret = attributes.get(Request.Keys.PathInfoCaret).getOrElse(0)
    requestUri.path.splitAt(caret)
  }
  def withPathInfo(pi: String) = copy(requestUri = requestUri.withPath(scriptName + pi))

  lazy val pathTranslated: Option[File] = attributes.get(Keys.PathTranslated)

  def queryString: String = requestUri.query.getOrElse("")

  lazy val remote: Option[InetAddress] = attributes.get(Keys.Remote)
  lazy val remoteAddr: Option[String] = remote.map(_.getHostAddress)
  lazy val remoteHost: Option[String] = remote.map(_.getHostName)

  lazy val remoteUser: Option[String] = None

  lazy val serverName = (requestUri.host orElse headers.get(Header.Host).map(_.host) getOrElse InetAddress.getLocalHost.getHostName)
  lazy val serverPort = (requestUri.port orElse headers.get(Header.Host).map(_.port) getOrElse 80)

  def serverSoftware: ServerSoftware = attributes.get(Keys.ServerSoftware).getOrElse(ServerSoftware.Unknown)
}

object Request {
  object Keys {
    val PathInfoCaret = AttributeKey.http4s[Int]("request.pathInfoCaret")
    val PathTranslated = AttributeKey.http4s[File]("request.pathTranslated")
    val Remote = AttributeKey.http4s[InetAddress]("request.remote")
    val ServerSoftware = AttributeKey.http4s[ServerSoftware]("request.serverSoftware")
  }
}

case class Response(
  status: Status = Status.Ok,
  headers: HeaderCollection = HeaderCollection.empty,
  body: HttpBody = HttpBody.empty,
  attributes: AttributeMap = AttributeMap.empty
) extends Message(headers, body, attributes) {
  type Self = Response
  def withHeaders(headers: HeaderCollection): Response = copy(headers = headers)
  def withBody(body: _root_.org.http4s.HttpBody): Response = copy(body = body)
}

