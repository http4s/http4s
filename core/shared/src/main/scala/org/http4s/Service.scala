package org.http4s

import cats.Functor
import cats.effect.kernel.Resource

import Service._

sealed abstract class Service[F[_], -A, B] {
  def run: A => Resource[F, Option[B]]

  def map[C](f: B => C): Service[F, A, C] =
    Run((a: A) => run(a).map(_.map(f)))
}

object Service extends ServiceKleisliFunctorInstance {
  private[Service] final case class Run[F[_], -A, B](run: A => Resource[F, Option[B]])
      extends Service[F, A, B]

  def apply[F[_], A, B](run: A => Resource[F, Option[B]]): Run[F, A, B] =
    Run(run)
}

private[http4s] sealed abstract class ServiceFunctor[F[_], A] extends Functor[Service[F, A, *]] {

  def map[B, C](fb: Service[F, A, B])(f: B => C): Service[F, A, C] =
    fb.map(f)
}

private[http4s] trait ServiceKleisliFunctorInstance {
  implicit def catsFunctorForHttp4sService[F[_], A, B]: Functor[Service[F, A, *]] =
    new ServiceFunctor[F, A] {}
}
