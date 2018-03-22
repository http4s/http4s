package org.http4s
package server

import cats.Monad
import cats.data.Kleisli
import cats.syntax.semigroupk._

object Router {

  import middleware.URITranslation.{translateRoot => translate}

  /**
    * Defines an HttpService based on list of mappings.
    * @see define
    */
  def apply[F[_]: Monad](mappings: (String, HttpService[F])*): HttpService[F] =
    define(mappings: _*)(HttpService.empty[F])

  /**
    * Defines an HttpService based on list of mappings and
    * a default Service to be used when none in the list match incomming requests.
    *
    * The mappings are processed in descending order (longest first) of prefix length.
    */
  def define[F[_]: Monad](mappings: (String, HttpService[F])*)(
      default: HttpService[F]): HttpService[F] =
    mappings.sortBy(_._1.length).foldLeft(default) {
      case (acc, (prefix, service)) =>
        if (prefix.isEmpty || prefix == "/") service <+> acc
        else
          Kleisli { req =>
            (
              if (req.pathInfo.startsWith(prefix))
                translate(prefix)(service) <+> acc
              else
                acc
            )(req)
          }
    }

}
