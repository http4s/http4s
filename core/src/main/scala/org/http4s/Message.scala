package org.http4s

import scalaz.stream.Process
import java.io.File
import java.net.{URI, InetAddress}
import org.http4s.util.CaseInsensitiveString
import scalaz.concurrent.Task

abstract class Message(headers: HeaderCollection, body: HttpBody, attributes: AttributeMap) {
  type Self <: Message

  def withHeaders(headers: HeaderCollection): Self

  def contentLength: Option[Int] = headers.get(Header.`Content-Length`).map(_.length)

  def contentType: Option[ContentType] = headers.get(Header.`Content-Type`).map(_.contentType)
  def withContentType(contentType: Option[ContentType]): Self =
    withHeaders(contentType match {
      case Some(ct) => headers.put(Header.`Content-Type`(ct))
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
  scriptName: String = "",
  pathInfo: String = "",
  queryString: String = "",
  protocol: ServerProtocol = ServerProtocol.`HTTP/1.1`,
  headers: HeaderCollection = HeaderCollection.empty,
  urlScheme: UrlScheme = HttpUrlScheme.Http,
  serverName: String = InetAddress.getLocalHost.getHostName,
  serverPort: Int = 80,
  serverSoftware: ServerSoftware = ServerSoftware.Unknown,
  remote: InetAddress = InetAddress.getLocalHost,
  body: HttpBody = HttpBody.empty,
  attributes: AttributeMap = AttributeMap.empty
) extends Message(headers, body, attributes) {
  import Request._

  type Self = Request
  def withHeaders(headers: HeaderCollection): Request = copy(headers = headers)
  def withBody(body: HttpBody): Request = copy(body = body)

  val uri: URI = new URI(urlScheme.toString, null, serverName, serverPort, scriptName+pathInfo, queryString, null)

  lazy val authType: Option[AuthScheme] = headers.get(Header.Authorization).map(_.credentials.authScheme)

  lazy val pathTranslated: Option[File] = attributes.get(Keys.PathTranslated)

  lazy val remoteAddr = remote.getHostAddress
  lazy val remoteHost = remote.getHostName

  lazy val remoteUser: Option[String] = None
}

object Request {
  object Keys {
    val PathTranslated = AttributeKey.http4s[File]("request.pathTranslated")
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

  def addCookie(cookie: Cookie): Response = addHeader(Header.Cookie(cookie))

  def removeCookie(cookie: Cookie): Response =
    addHeader(Header.`Set-Cookie`(cookie.copy(content = "", expires = Some(UnixEpoch), maxAge = Some(0))))

  def withStatus[T <% Status](status: T) = copy(status = status)
}

object Response {
  implicit def toTask(resp: Response): Task[Response] = Task.now(resp)
}
