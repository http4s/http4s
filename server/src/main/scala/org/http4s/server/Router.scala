package org.http4s
package server

import cats.data.Kleisli
import cats.effect._
import cats.implicits._

object Router {

  import middleware.URITranslation.{translateRoot => translate}

  /**
    * Defines an [[HttpRoutes]] based on list of mappings.
    * @see define
    */
  def apply[F[_]: Sync](mappings: (String, HttpRoutes[F])*): HttpRoutes[F] =
    define(mappings: _*)(HttpRoutes.empty[F])

  /**
    * Defines an [[HttpRoutes]] based on list of mappings and
    * a default Service to be used when none in the list match incomming requests.
    *
    * The mappings are processed in descending order (longest first) of prefix length.
    */
  def define[F[_]: Sync](mappings: (String, HttpRoutes[F])*)(
      default: HttpRoutes[F]): HttpRoutes[F] =
    mappings.sortBy(_._1.length).foldLeft(default) {
      case (acc, (prefix, routes)) =>
        if (prefix.isEmpty || prefix == "/") routes <+> acc
        else
          Kleisli { req =>
            (
              if (req.pathInfo.startsWith(prefix))
                translate(prefix)(routes) <+> acc
              else
                acc
            )(req)
          }
    }

}
