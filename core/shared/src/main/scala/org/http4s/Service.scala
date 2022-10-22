package org.http4s

import cats.Monad
import cats.StackSafeMonad
import cats.effect.kernel.Resource

import Service._

sealed abstract class Service[F[_], -A, B] {
  def run: A => Resource[F, Option[B]]

  def map[C](f: B => C): Service[F, A, C] =
    apply((a: A) => run(a).map(_.map(f)))

  def flatMap[AA <: A, C](f: B => Service[F, AA, C]) =
    apply((a: AA) =>
      run(a).flatMap(_ match {
        case Some(b) => f(b).run(a)
        case None => Resource.pure[F, Option[C]](None)
      })
    )
}

object Service extends ServiceKleisliMonadInstance {
  private[Service] final case class Run[F[_], -A, B](run: A => Resource[F, Option[B]])
      extends Service[F, A, B]

  def apply[F[_], A, B](run: A => Resource[F, Option[B]]): Run[F, A, B] =
    Run(run)

  def pure[F[_], A, B](b: B): Service[F, A, B] =
    apply(Function.const(Resource.pure(Some(b))))
}

private[http4s] sealed abstract class ServiceMonad[F[_], A]
    extends Monad[Service[F, A, *]]
    with StackSafeMonad[Service[F, A, *]] {
  override def map[B, C](fb: Service[F, A, B])(f: B => C): Service[F, A, C] =
    fb.map(f)

  def pure[B](b: B): Service[F, A, B] =
    Service.pure(b)

  def flatMap[B, C](fb: Service[F, A, B])(f: B => Service[F, A, C]): Service[F, A, C] =
    fb.flatMap(f)
}

private[http4s] trait ServiceKleisliMonadInstance {
  implicit def catsMonadForHttp4sService[F[_], A, B]: Monad[Service[F, A, *]] =
    new ServiceMonad[F, A] {}
}
