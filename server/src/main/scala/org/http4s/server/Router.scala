package org.http4s.server

import org.http4s.server.middleware.URITranslation
import org.http4s.{Status, Response, Request}

import scalaz.concurrent.Task

object Router {
  def apply(mappings: (String, HttpService)*): HttpService = {
    // we need to `orElse` any that match
    var table = Map.empty[String, HttpService]
    mappings.sortBy(_._1.length).reverse.foreach { case (prefix, service) =>
      val transformed =
        if (prefix.isEmpty || prefix == "/") service
        else URITranslation.translateRoot(prefix)(service)
      //prefix -> transformed
      table.get(prefix) match {
        case Some(service) => table += prefix -> service.orElse(transformed)
        case None          => table += prefix -> transformed
      }
    }

    HttpService.lift { req =>
      table.find { case (prefix, _) =>
        req.pathInfo.startsWith(prefix)
      }.fold(HttpService.empty)(_._2)(req)
    }
  }
}
