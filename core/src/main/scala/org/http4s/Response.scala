package org.http4s

import scala.concurrent.Future

import play.api.libs.iteratee.Enumerator

trait Response

case class HttpResponse(
  statusLine: StatusLine = StatusLine.Ok,
  headers: ResponseHeaders = ResponseHeaders.Empty,
  entityBody: Enumerator[Array[Byte]] = Enumerator.eof,
  trailer: Future[Option[Headers]] = Future.successful(None)
) extends Response

case class StatusLine(code: Int, reason: String)
object StatusLine {
  val Ok = StatusLine(200, "OK")
}

trait ResponseHeaders extends Headers

object ResponseHeaders {
  val Empty = ???
}

trait Websocket extends Response

/*
// Controversial. Would live in the servlet module. For legacy integration only.
// Non-servlet backends would respond with some sort of 500.
sealed trait ServletResponse extends Response {
  def respond: (HttpServletRequest, HttpServletResponse) => Unit
}
*/
