package org.http4s
package server

import cats._
import cats.data.Kleisli
import cats.syntax.semigroupk._

object Router {

  /**
    * Defines an [[HttpRoutes]] based on list of mappings.
    * @see define
    */
  def apply[F[_]: Monad](mappings: (String, HttpRoutes[F])*): HttpRoutes[F] =
    define(mappings: _*)(HttpRoutes.empty[F])

  /**
    * Defines an [[HttpRoutes]] based on list of mappings and
    * a default Service to be used when none in the list match incomming requests.
    *
    * The mappings are processed in descending order (longest first) of prefix length.
    */
  def define[F[_]: Monad](mappings: (String, HttpRoutes[F])*)(
      default: HttpRoutes[F]): HttpRoutes[F] =
    mappings.sortBy(_._1.length).foldLeft(default) {
      case (acc, (prefix, routes)) =>
        val prefixSegments = toSegments(prefix)
        if (prefixSegments.isEmpty) routes <+> acc
        else
          Kleisli { req =>
            (
              if (toSegments(req.pathInfo).startsWith(prefixSegments)) {
                routes.local(translate(prefix)) <+> acc
              } else
                acc
            )(req)
          }
    }

  private[server] def translate[F[_]: Functor](prefix: String)(req: Request[F]): Request[F] = {
    val newCaret = prefix match {
      case "/" => 0
      case x if x.startsWith("/") => x.length
      case x => x.length + 1
    }

    val oldCaret = req.attributes
      .lookup(Request.Keys.PathInfoCaret)
      .getOrElse(0)
    req.withAttribute(Request.Keys.PathInfoCaret, oldCaret + newCaret)
  }

  private[server] def toSegments(path: String): List[String] =
    path.split("/").filterNot(_.trim.isEmpty).toList
}
