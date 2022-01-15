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

final case class WithContext[Msg, A](context: A, message: Msg) {

  def mapContext[B](f: A => B): WithContext[Msg, B] =
    WithContext(f(context), message)

}

object WithContext {

  implicit class ContextRequestOps[A, F[_]](private val creq: ContextRequest[F, A]) extends AnyVal {
    def req: Request[F] = creq.message

    def mapK[G[_]](fk: F ~> G): ContextRequest[G, A] =
      WithContext(creq.context, creq.message.mapK(fk))
  }

  implicit class ContextResponseOps[A, F[_]](private val creq: ContextResponse[F, A])
      extends AnyVal {
    def response: Response[F] = creq.message

    def mapK[G[_]](fk: F ~> G): ContextResponse[G, A] =
      WithContext(creq.context, creq.message.mapK(fk))
  }

  implicit def withContextInstances[M]: NonEmptyTraverse[WithContext[M, *]] =
    new NonEmptyTraverse[WithContext[M, *]] {
      override def foldLeft[A, B](fa: WithContext[M, A], b: B)(f: (B, A) => B): B =
        f(b, fa.context)
      override def foldRight[A, B](fa: WithContext[M, A], lb: Eval[B])(
          f: (A, Eval[B]) => Eval[B]
      ): Eval[B] =
        f(fa.context, lb)
      override def nonEmptyTraverse[G[_]: Apply, A, B](fa: WithContext[M, A])(
          f: A => G[B]
      ): G[WithContext[M, B]] =
        f(fa.context).map(b => WithContext(b, fa.message))
      def reduceLeftTo[A, B](fa: WithContext[M, A])(f: A => B)(g: (B, A) => B): B =
        f(fa.context)
      def reduceRightTo[A, B](fa: WithContext[M, A])(f: A => B)(
          g: (A, Eval[B]) => Eval[B]
      ): Eval[B] =
        Eval.later(f(fa.context))
    }
}

object ContextRequest {
  def apply[F[_], T](t: T, req: Request[F]): ContextRequest[F, T] =
    WithContext(t, req)

  def unapply[F[_], T](creq: WithContext[Request[F], T]): Option[(T, Request[F])] =
    Some((creq.context, creq.message))

  def apply[F[_]: Functor, T](
      getContext: Request[F] => F[T]
  ): Kleisli[F, Request[F], ContextRequest[F, T]] =
    Kleisli(request => getContext(request).map(ctx => WithContext(ctx, request)))

}

object ContextResponse {

  def apply[F[_], T](t: T, res: Response[F]): ContextResponse[F, T] =
    WithContext(t, res)

  def unapply[F[_], T](cres: ContextResponse[F, T]): Option[(T, Response[F])] =
    Some((cres.context, cres.message))
}
