package org.http4s
package server

import cats._
import cats.effect._
import cats.implicits._

object Router {

  import middleware.URITranslation.{translateRoot => translate}

  /**
    * Defines an HttpService based on list of mappings.
    * @see define
    */
  def apply[F[_]: Sync](mappings: (String, HttpService[F])*)
                       (implicit F: Semigroup[F[MaybeResponse[F]]]): HttpService[F] =
    define(mappings:_*)(HttpService.empty[F])

  /**
    * Defines an HttpService based on list of mappings,
    * a default Service to be used when none in the list match incomming requests,
    * and an implicit Fallthrough which decides whether a request was matched.
    *
    * The mappings are processed in descending order (longest first) of prefix length.
    */
  def define[F[_]: Sync](mappings: (String, HttpService[F])*)
                        (default: HttpService[F])
                        (implicit F: Semigroup[F[MaybeResponse[F]]]): HttpService[F] =
    mappings.sortBy(_._1.length).foldLeft(default) {
      case (acc, (prefix, service)) =>
        if (prefix.isEmpty || prefix == "/") service |+| acc
        else HttpService.lift {
          req => (
            if (req.pathInfo.startsWith(prefix))
              translate(prefix)(service) |+| acc
            else
              acc
          )(req)
        }
    }

}
