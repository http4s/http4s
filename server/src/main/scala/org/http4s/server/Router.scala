package org.http4s
package server

import middleware.URITranslation.{translateRoot => translate}
import syntax.ServiceOps

object Router {

  def apply(mappings: (String, HttpService)*): HttpService = define(mappings:_*)()

  def define(mappings: (String, HttpService)*)(default: HttpService = HttpService.empty): HttpService =
    mappings.sortBy(_._1.length).foldLeft(default) {
      case (acc, (prefix, service)) =>
        if (prefix.isEmpty || prefix == "/") service orElse acc
        else HttpService.lift {
          req => (
            if (req.pathInfo.startsWith(prefix)) translate(prefix)(service) orElse acc
            else acc
          ) (req)
        }
    }

}
