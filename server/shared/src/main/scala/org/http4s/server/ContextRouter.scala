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

package org.http4s.server

import cats.Contravariant
import cats.Functor
import cats.Monad
import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.Sync
import cats.syntax.all._
import org.http4s.ContextRequest
import org.http4s.ContextRoutes
import org.http4s.Request
import org.http4s.Uri

object ContextRouter {

  object Segment {
    implicit def functor[F[_]: Functor, X]: Functor[Segment[F, X, *]] =
      new Functor[Segment[F, X, *]] {
        override def map[A, B](fa: Segment[F, X, A])(f: A => B): Segment[F, X, B] =
          new Segment(fa.run(_, _).map(f))
      }

    implicit def contravariant[F[_], X]: Contravariant[Segment[F, *, X]] =
      new Contravariant[Segment[F, *, X]] {
        override def contramap[A, B](fa: Segment[F, A, X])(f: B => A): Segment[F, B, X] =
          new Segment((b, segment) => fa.run(f(b), segment))
      }

    class Partial[A] {
      def apply[F[_], B](run: (A, Uri.Path.Segment) => OptionT[F, B]): Segment[F, A, B] =
        new Segment(run)
    }
    // partially apply for inference
    def apply[A] = new Partial[A]
  }

  final class Segment[F[_], A, B](val run: (A, Uri.Path.Segment) => OptionT[F, B]) {
    def apply(routes: ContextRoutes[B, F])(implicit F: Monad[F]): ContextRoutes[A, F] =
      Kleisli { case ContextRequest(a, req) =>
        for {
          head <- OptionT.fromOption[F](req.pathInfo.segments.headOption)
          b <- run(a, head)
          caret = req.attributes.lookup(Request.Keys.PathInfoCaret).getOrElse(0)
          response <- routes(
            ContextRequest(b, req.withAttribute(Request.Keys.PathInfoCaret, caret + 1))
          )
        } yield response
      }

    def ->(routes: ContextRoutes[B, F]): Routable[F, A] =
      Routable.Dynamic(this, routes)
  }

  object Routable {
    final case class Static[F[_], A](tupled: (String, ContextRoutes[A, F])) extends Routable[F, A]
    final case class Dynamic[F[_], A, B](segment: Segment[F, A, B], routes: ContextRoutes[B, F])
        extends Routable[F, A]

    implicit def tuple[F[_], A](tupled: (String, ContextRoutes[A, F])): Routable[F, A] =
      Static(tupled)
  }
  sealed trait Routable[F[_], A]

  /** Defines an [[ContextRoutes]] based on list of mappings.
    * @see define
    */
  def apply[F[_]: Monad, A](mappings: (String, ContextRoutes[A, F])*): ContextRoutes[A, F] =
    define(mappings: _*)(ContextRoutes.empty[A, F])

  @deprecated("Kept for binary compatiblity.  Use the apply with the Monad constraint.", "0.23.12")
  def apply[F[_], A](
      mappings: Seq[(String, ContextRoutes[A, F])],
      sync: Sync[F],
  ): ContextRoutes[A, F] =
    defineHelper(mappings, ContextRoutes.empty[A, F](sync))(sync)

  /** Defines an [[ContextRoutes]] based on list of mappings and
    * a default Service to be used when none in the list match incoming requests.
    *
    * The mappings are processed in descending order (longest first) of prefix length.
    */
  def define[F[_]: Monad, A](
      mappings: (String, ContextRoutes[A, F])*
  )(default: ContextRoutes[A, F]): ContextRoutes[A, F] =
    defineHelper(mappings, default)

  @deprecated("Kept for binary compatiblity.  Use the define with the Monad constraint.", "0.23.12")
  def define[F[_], A](
      mappings: Seq[(String, ContextRoutes[A, F])]
  )(default: ContextRoutes[A, F], sync: Sync[F]): ContextRoutes[A, F] =
    defineHelper(mappings, default)(sync)

  // Supports the deprecated methods.  We can't call the Sync
  // version of define after defining the new Monad version.
  private def defineHelper[F[_]: Monad, A](
      mappings: Seq[(String, ContextRoutes[A, F])],
      default: ContextRoutes[A, F],
  ): ContextRoutes[A, F] =
    mappings.sortBy(_._1.length).foldLeft(default) { case (acc, (prefix, routes)) =>
      val prefixSegments = Uri.Path.unsafeFromString(prefix)
      if (prefixSegments.isEmpty) routes <+> acc
      else
        Kleisli { case creq @ ContextRequest(_, req) =>
          if (req.pathInfo.startsWith(prefixSegments)) {
            val treq = Router.translate(prefixSegments)(creq.req)
            routes(creq.copy(req = treq)).orElse(acc(creq))
          } else acc(creq)
        }
    }

  def of[F[_]: Sync, A](mappings: Routable[F, A]*): ContextRoutes[A, F] =
    dynamic(mappings: _*)(ContextRoutes.empty[A, F])

  def dynamic[F[_]: Sync, A](mappings: Routable[F, A]*)(
      default: ContextRoutes[A, F]
  ): ContextRoutes[A, F] = {
    val (statics, dynamic) = mappings.toList.partitionEither {
      case Routable.Static(tuple) => Left(tuple)
      case Routable.Dynamic(segment, route) => Right(segment(route))
    }
    dynamic.foldLeft(define(statics: _*)(default))(_ <+> _)
  }
}
