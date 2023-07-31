/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package server

import cats._
import cats.data.Kleisli
import cats.data.OptionT
import cats.syntax.all._

object Router {

  object Segment {
    implicit def instances[F[_]: Functor]: Functor[Segment[F, *]] =
      new Functor[Segment[F, *]] {
        override def map[A, B](fa: Segment[F, A])(f: A => B): Segment[F, B] =
          Segment(fa.run(_).map(f))
      }
  }

  final case class Segment[F[_], A](run: Uri.Path.Segment => OptionT[F, A]) {
    def apply(routes: ContextRoutes[A, F])(implicit F: Monad[F]): HttpRoutes[F] =
      Kleisli { req =>
        for {
          head <- OptionT.fromOption[F](req.pathInfo.segments.headOption)
          a <- run(head)
          caret = req.attributes.lookup(Request.Keys.PathInfoCaret).getOrElse(0)
          response <- routes(
            ContextRequest(a, req.withAttribute(Request.Keys.PathInfoCaret, caret + 1))
          )
        } yield response
      }

    def ->(routes: ContextRoutes[A, F]): Routable[F] =
      Routable.Dynamic(this, routes)
  }

  object Routable {
    final case class Static[F[_]](tupled: (String, HttpRoutes[F])) extends Routable[F]
    final case class Dynamic[F[_], A](segment: Segment[F, A], routes: ContextRoutes[A, F])
        extends Routable[F]

    implicit def tuple[F[_]](tupled: (String, HttpRoutes[F])): Routable[F] =
      Static(tupled)
  }
  sealed trait Routable[F[_]]

  /** Defines an [[HttpRoutes]] based on list of mappings.
    * @see define
    */
  def apply[F[_]: Monad](mappings: (String, HttpRoutes[F])*): HttpRoutes[F] =
    define(mappings: _*)(HttpRoutes.empty[F])

  /** Defines an [[HttpRoutes]] based on list of mappings and
    * a default Service to be used when none in the list match incoming requests.
    *
    * The mappings are processed in descending order (longest first) of prefix length.
    */
  def define[F[_]: Monad](
      mappings: (String, HttpRoutes[F])*
  )(default: HttpRoutes[F]): HttpRoutes[F] =
    mappings.sortBy(_._1.length).foldLeft(default) { case (acc, (prefix, routes)) =>
      val prefixPath = Uri.Path.unsafeFromString(prefix)
      if (prefixPath.isEmpty) routes <+> acc
      else
        Kleisli { req =>
          if (req.pathInfo.startsWith(prefixPath))
            routes(translate(prefixPath)(req)).orElse(acc(req))
          else
            acc(req)
        }
    }

  def of[F[_]: Monad](mappings: Routable[F]*): HttpRoutes[F] =
    dynamic(mappings: _*)(HttpRoutes.empty[F])

  def dynamic[F[_]: Monad](
      mappings: Routable[F]*
  )(default: HttpRoutes[F]): HttpRoutes[F] = {
    val (statics, dynamic) = mappings.toList.partitionEither {
      case Routable.Static(tupled) => Left(tupled)
      case Routable.Dynamic(segment, route) => Right(segment(route))
    }
    dynamic.foldLeft(define(statics: _*)(default))(_ <+> _)
  }

  // it should be used only if the `prefix` corresponds to the `req`'s path
  private[server] def translate[F[_]](prefix: Uri.Path)(req: Request[F]): Request[F] = {
    val newCaret = prefix.segments.size
    val oldCaret = req.attributes.lookup(Request.Keys.PathInfoCaret).getOrElse(0)
    val resultCaret = oldCaret + newCaret

    resultCaret match {
      case i if i == 0 => req
      case index => req.withAttribute(Request.Keys.PathInfoCaret, index)
    }
  }
}
