package org.http4s

import org.http4s.Writable.Entity

import scalaz.concurrent.Task

object ResponseBuilder {

//  def Ok[A](body: A, headers: Headers = Headers.empty)(implicit w: Writable[A]): Task[Response] =
//    response(Status.Ok, body, headers)

  def apply[A](status: Status, body: A, headers: Headers = Headers.empty)(implicit w: Writable[A]): Task[Response] = {
    var h = headers ++ w.headers
    w.toEntity(body).flatMap { case Entity(proc, len) =>
      len foreach { h +:= Header.`Content-Length`(_) }
      basic(status = status, headers = h, body = proc)
    }
  }

  def basic(status: Status, headers: Headers = Headers.empty, body: EntityBody = EmptyBody): Task[Response] = {
    Task.now(Response(status = status, headers = headers, body = body))
  }

  def notFound(request: Request): Task[Response] = {
    val body = s"${request.pathInfo} not found"
    apply(Status.NotFound, body)
  }
}
