package org.http4s.server

import org.http4s.server.middleware.URITranslation
import org.http4s.{Status, Response, Request}

import scalaz.concurrent.Task

class Router private (mappings: Seq[(String, HttpService)]) extends HttpService {
  // TODO A more efficient implementation does not require much imagination
  def apply(req: Request): Task[Response] = {
    HttpService.empty(req)
    val service = mappings.find { case (prefix, _) =>
      req.pathInfo.startsWith(prefix)
    }.fold(HttpService.empty)(_._2 orElse HttpService.empty)
    service(req)
  }
}

object Router {
  def apply(mappings: Seq[(String, HttpService)]): Router = new Router(
    mappings.sortBy(_._1.length).reverse.map { case (prefix, service) =>
      val transformed =
        if (prefix.isEmpty || prefix == "/") service
        else URITranslation.translateRoot(prefix)(service)
      prefix -> transformed
    }
  )
}
