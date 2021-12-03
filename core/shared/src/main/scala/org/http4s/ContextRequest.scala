/*
 * Copyright 2013 http4s.org
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

import cats._
import cats.data.Kleisli
import cats.syntax.all._

final case class ContextRequest[F[_], A](context: A, req: Request[F]) {
  def mapK[G[_]](fk: F ~> G): ContextRequest[G, A] =
    ContextRequest(context, req.mapK(fk))

  @deprecated("Use context instead", "0.21.0")
  def authInfo: A = context
}

object ContextRequest {
  def apply[F[_]: Functor, T](
      getContext: Request[F] => F[T]
  ): Kleisli[F, Request[F], ContextRequest[F, T]] =
    Kleisli(request => getContext(request).map(ctx => ContextRequest(ctx, request)))

  implicit def contextRequestInstances[F[_]]: NonEmptyTraverse[ContextRequest[F, *]] =
    new NonEmptyTraverse[ContextRequest[F, *]] {
      override def foldLeft[A, B](fa: ContextRequest[F, A], b: B)(f: (B, A) => B): B =
        f(b, fa.context)
      override def foldRight[A, B](fa: ContextRequest[F, A], lb: Eval[B])(
          f: (A, Eval[B]) => Eval[B]
      ): Eval[B] =
        f(fa.context, lb)
      override def nonEmptyTraverse[G[_]: Apply, A, B](fa: ContextRequest[F, A])(
          f: A => G[B]
      ): G[ContextRequest[F, B]] =
        f(fa.context).map(b => ContextRequest(b, fa.req))
      def reduceLeftTo[A, B](fa: ContextRequest[F, A])(f: A => B)(g: (B, A) => B): B =
        f(fa.context)
      def reduceRightTo[A, B](fa: ContextRequest[F, A])(f: A => B)(
          g: (A, Eval[B]) => Eval[B]
      ): Eval[B] =
        Eval.later(f(fa.context))
    }
}
