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

import cats.Alternative
import cats.Applicative
import cats.Monad
import cats.StackSafeMonad
import cats.effect.kernel.Resource
import cats.mtl.Local
import cats.syntax.all._

import AltService._

sealed abstract class AltService[F[_], -A, B] {
  protected[AltService] def run: A => Resource[F, Option[B]]

  def applyOrElse[AA <: A](a: AA, f: AA => Resource[F, B]): Resource[F, B] =
    run(a).flatMap(_ match {
      case Some(b) => Resource.pure(b)
      case None => f(a)
    })

  def map[C](f: B => C): AltService[F, A, C] =
    apply((a: A) => run(a).map(_.map(f)))

  def flatMap[AA <: A, C](f: B => AltService[F, AA, C]) =
    apply((a: AA) =>
      run(a).flatMap(_ match {
        case Some(b) => f(b).run(a)
        case None => Resource.pure[F, Option[C]](None)
      })
    )

  def orElse[AA <: A](default: => AltService[F, AA, B]): AltService[F, AA, B] =
    apply(a =>
      run(a).flatMap {
        case some: Some[_] => Resource.pure(some)
        case None => default.run(a)
      }
    )

  def local[C](f: C => A): AltService[F, C, B] =
    apply(c => run(f(c)))
}

object AltService extends AltServiceInstances {
  private[AltService] final case class Run[F[_], -A, B](run: A => Resource[F, Option[B]])
      extends AltService[F, A, B]

  def apply[F[_], A, B](run: A => Resource[F, Option[B]]): Run[F, A, B] =
    Run(run)

  def pure[F[_], A, B](b: B): AltService[F, A, B] =
    apply(Function.const(Resource.pure(Some(b))))

  def pass[F[_], A, B]: AltService[F, A, B] =
    apply(Function.const(Resource.pure(None)))

  def ask[F[_], A]: AltService[F, A, A] =
    apply(a => Resource.pure(Some(a)))

  def of[F[_]: Monad, A, B](pf: PartialFunction[A, Resource[F, B]]): AltService[F, A, B] =
    apply(a => Resource.unit.flatMap(_ => pf.lift(a).sequence))
}

private[http4s] sealed abstract class AltServiceInstance[F[_], A]
    extends Monad[AltService[F, A, *]]
    with StackSafeMonad[AltService[F, A, *]]
    with Alternative[AltService[F, A, *]] {
  override def map[B, C](fb: AltService[F, A, B])(f: B => C): AltService[F, A, C] =
    fb.map(f)

  def pure[B](b: B): AltService[F, A, B] =
    AltService.pure(b)

  def flatMap[B, C](fb: AltService[F, A, B])(f: B => AltService[F, A, C]): AltService[F, A, C] =
    fb.flatMap(f)

  def combineK[B](x: AltService[F, A, B], y: AltService[F, A, B]): AltService[F, A, B] =
    x.orElse(y)

  def empty[B]: AltService[F, A, B] =
    AltService.pass
}

private[http4s] trait AltServiceInstances {
  implicit def catsInstancesForHttp4sAltService[F[_], A]
      : Monad[AltService[F, A, *]] with Alternative[AltService[F, A, *]] =
    new AltServiceInstance[F, A] {}

  implicit def catsMtlLocalForHttp4sAltService[F[_], A]: Local[AltService[F, A, *], A] =
    new Local[AltService[F, A, *], A] {
      val applicative = Applicative[AltService[F, A, *]]
      def ask[E >: A]: AltService[F, A, E] =
        AltService.ask
      def local[B](fb: AltService[F, A, B])(f: A => A): AltService[F, A, B] =
        fb.local(f)
    }
}
