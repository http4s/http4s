package org.http4s.util

import org.http4s.Writable.Entity
import org.http4s._

import scala.util.control.NoStackTrace
import scalaz.concurrent.Task

case class ResponseException(status: Status,
                             body: EntityBody,
                             headers: Headers) extends Exception() with NoStackTrace {

  def asResponse(version: HttpVersion = HttpVersion.`HTTP/1.1`) =
    Response(status, version, headers, body)
}

object ResponseException {
  def BadRequest[A](body: A, headers: Header*)(implicit r: Writable[A]): Task[Nothing] = {
    r.toEntity(body).flatMap { case Entity(body, len) =>
      val hs = Headers(headers.toList) ++ r.headers ++ len.map(len => Header.`Content-Length`(len))
      Task.fail(ResponseException(Status.BadRequest, body, hs))}
  }
}
