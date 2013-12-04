package org.http4s

import scalaz.stream.Process

sealed trait Message {
  type Self <: Message

  def headers: HeaderCollection

  def withHeaders(headers: HeaderCollection): Self

  def contentLength: Option[Int] = headers.get(Header.`Content-Length`).map(_.length)

  def contentType: Option[ContentType] = headers.get(Header.`Content-Type`).map(_.contentType)
  def withContentType(contentType: Option[ContentType]): Self =
    withHeaders(contentType match {
      case Some(ct) => headers.put(Header.`Content-Type`(ct))
      case None => headers.filterNot(_.is(Header.`Content-Type`))
    })

  def charset: CharacterSet = contentType.map(_.charset) getOrElse CharacterSet.`ISO-8859-1`

  def body: HttpBody

  def withBody(body: HttpBody): Self

  def addHeader(header: Header): Self = withHeaders(headers = header +: headers)

  def dropHeaders(f: Header => Boolean): Self =
    withHeaders(headers = headers.filter(f))

  def dropHeader(key: HeaderKey): Self = dropHeaders(_ isNot key)

  def isChunked: Boolean = headers.get(Header.`Transfer-Encoding`)
    .map(_.values.list.contains(TransferCoding.chunked))
    .getOrElse(false)
}

case class Request(
  prelude: RequestPrelude = RequestPrelude(),
  body: HttpBody = HttpBody.empty
) extends Message {
  type Self = Request
  def headers: HeaderCollection = prelude.headers
  def withHeaders(headers: HeaderCollection): Request = copy(prelude = prelude.copy(headers = headers))
  def withBody(body: HttpBody): Request = copy(body = body)
}

case class Response(
  prelude: ResponsePrelude = ResponsePrelude(),
  body: HttpBody = HttpBody.empty,
  attributes: AttributeMap = AttributeMap.empty
) extends Message {
  type Self = Response
  def headers: HeaderCollection = prelude.headers
  def withHeaders(headers: HeaderCollection): Response = copy(prelude = prelude.copy(headers = headers))
  def withBody(body: _root_.org.http4s.HttpBody): Response = copy(body = body)

  def addCookie(cookie: Cookie): Response = addHeader(Header.Cookie(cookie))

  def removeCookie(cookie: Cookie): Response =
    addHeader(Header.`Set-Cookie`(cookie.copy(content = "", expires = Some(UnixEpoch), maxAge = Some(0))))

  def status: Status = prelude.status

  def status[T <% Status](status: T) = copy(prelude.copy(status = status))
}
