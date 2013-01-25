package org.http4s

import scala.concurrent.Future

import play.api.libs.iteratee.Enumerator

trait Result

case class HttpResponse(
  statusLine: StatusLine = StatusLine.Ok,
  headers: ResponseHeaders = ResponseHeaders.Empty,
  entityBody: Enumerator[Array[Byte]] = Enumerator.eof,
  trailer: Future[Option[Headers]] = Future.successful(None)
) extends Result

case class StatusLine(code: Int, reason: String)
object StatusLine {
  val Ok = StatusLine(200, "OK")
}

trait ResponseHeaders extends Headers

object ResponseHeaders {
  val Empty = ???
}

trait WebSocket extends Result
