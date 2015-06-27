package org.http4s.server

import org.http4s.server.middleware.URITranslation
import org.http4s.{Status, Response, Request}

import scalaz.concurrent.Task

case class Router (mappings: Seq[(String, HttpService)]) extends HttpService {
  lazy val prefixedMappings: Seq[(String, HttpService)] = mappings.map { case (prefix, service) =>
    val transformed =
      if (prefix.isEmpty || prefix == "/") service
      else URITranslation.translateRoot(prefix)(service)
    prefix -> transformed
  }

  // TODO A more efficient implementation does not require much imagination
  def apply(req: Request): Task[Response] = {
    prefixedMappings.find { case (prefix, _) =>
      req.pathInfo.startsWith(prefix)
    }.fold({_: Request => Task.now(Response(Status.NotFound))})(_._2)(req)
  }
}
