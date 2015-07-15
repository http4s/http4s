package org.http4s.server

import org.http4s.server.middleware.URITranslation
import org.http4s.{Status, Response, Request}

import scalaz.concurrent.Task

object Router {
  // TODO A more efficient implementation does not require much imagination
  def apply(mappings: (String, HttpService)*): HttpService = {
    val table = mappings.sortBy(_._1.length).reverse.map { case (prefix, service) =>
      val transformed =
        if (prefix.isEmpty || prefix == "/") service
        else URITranslation.translateRoot(prefix)(service)
      prefix -> transformed
    }

    Service.lift { req =>
      table.find { case (prefix, _) =>
        req.pathInfo.startsWith(prefix)
      }.fold(HttpService.empty)(_._2)(req)
    }
  }
}
