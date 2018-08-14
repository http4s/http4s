package org.http4s
package server

import cats.Functor
import cats.data.Kleisli
import cats.effect._
import cats.implicits._

object Router {

  /**
    * Defines an HttpService based on list of mappings.
    * @see define
    */
  def apply[F[_]: Sync](mappings: (String, HttpService[F])*): HttpService[F] =
    define(mappings: _*)(HttpService.empty[F])

  /**
    * Defines an HttpService based on list of mappings and
    * a default Service to be used when none in the list match incomming requests.
    *
    * The mappings are processed in descending order (longest first) of prefix length.
    */
  def define[F[_]: Sync](mappings: (String, HttpService[F])*)(
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

  private def translate[F[_]: Functor](prefix: String)(service: HttpService[F]): HttpService[F] = {
    val newCaret = prefix match {
      case "/" => 0
      case x if x.startsWith("/") => x.length
      case x => x.length + 1
    }

    service.local { req: Request[F] =>
      val oldCaret = req.attributes
        .get(Request.Keys.PathInfoCaret)
        .getOrElse(0)
      req.withAttribute(Request.Keys.PathInfoCaret(oldCaret + newCaret))
    }
  }

}
