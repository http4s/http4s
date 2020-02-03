package org.http4s.server

import org.http4s.{ContextRequest, ContextRoutes}
import cats.data.Kleisli
import cats.effect.Sync
import cats.syntax.semigroupk._

object ContextRouter {

  /**
    * Defines an [[ContextRoutes]] based on list of mappings.
    * @see define
    */
  def apply[F[_]: Sync, A](mappings: (String, ContextRoutes[A, F])*): ContextRoutes[A, F] =
    define(mappings: _*)(ContextRoutes.empty[A, F])

  /**
    * Defines an [[ContextRoutes]] based on list of mappings and
    * a default Service to be used when none in the list match incoming requests.
    *
    * The mappings are processed in descending order (longest first) of prefix length.
    */
  def define[F[_]: Sync, A](
      mappings: (String, ContextRoutes[A, F])*
  )(default: ContextRoutes[A, F]): ContextRoutes[A, F] =
    mappings.sortBy(_._1.length).foldLeft(default) {
      case (acc, (prefix, routes)) =>
        val segments = Router.toSegments(prefix)
        if (segments.isEmpty) routes <+> acc
        else
          Kleisli { req =>
            (
              if (Router.toSegments(req.req.pathInfo).startsWith(segments))
                routes
                  .local[ContextRequest[F, A]](r =>
                    ContextRequest(r.context, Router.translate(prefix)(r.req))) <+> acc
              else
                acc
            )(req)
          }
    }
}
