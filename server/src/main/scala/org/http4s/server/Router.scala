package org.http4s
package server

import org.http4s.batteries._

object Router {

  import Service.{withFallback => fallback}
  import middleware.URITranslation.{translateRoot => translate}

  /**
    * Defines an HttpService based on list of mappings.
    * @see define
    */
  def apply(mappings: (String, HttpService)*): HttpService = define(mappings:_*)()

  /**
    * Defines an HttpService based on list of mappings,
    * a default Service to be used when none in the list match incomming requests,
    * and an implicit Fallthrough which decides whether a request was matched.
    *
    * The mappings are processed in descending order (longest first) of prefix length.
    */
  def define(mappings: (String, HttpService)*)
            (default: HttpService = HttpService.empty): HttpService =
    mappings.sortBy(_._1.length).foldLeft(default) {
      case (acc, (prefix, service)) =>
        if (prefix.isEmpty || prefix == "/") service |+| acc
        else HttpService.lift {
          req => (
            if (req.pathInfo.startsWith(prefix))
              translate(prefix)(service) |+| acc
            else
              acc
          ) (req)
        }
    }

}
