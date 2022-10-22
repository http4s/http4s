package org.http4s

import cats.Monad
import cats.MonoidK
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

  def orElse[AA <: A](default: => Service[F, AA, B]): Service[F, AA, B] =
    apply(a =>
      run(a).flatMap {
        case some: Some[_] => Resource.pure(some)
        case None => default.run(a)
      }
    )
}

object Service extends ServiceInstances {
  private[Service] final case class Run[F[_], -A, B](run: A => Resource[F, Option[B]])
      extends Service[F, A, B]

  def apply[F[_], A, B](run: A => Resource[F, Option[B]]): Run[F, A, B] =
    Run(run)

  def pure[F[_], A, B](b: B): Service[F, A, B] =
    apply(Function.const(Resource.pure(Some(b))))

  def pass[F[_], A, B]: Service[F, A, B] =
    apply(Function.const(Resource.pure(None)))
}

private[http4s] sealed abstract class ServiceInstance[F[_], A]
    extends Monad[Service[F, A, *]]
    with StackSafeMonad[Service[F, A, *]]
    with MonoidK[Service[F, A, *]] {
  override def map[B, C](fb: Service[F, A, B])(f: B => C): Service[F, A, C] =
    fb.map(f)

  def pure[B](b: B): Service[F, A, B] =
    Service.pure(b)

  def flatMap[B, C](fb: Service[F, A, B])(f: B => Service[F, A, C]): Service[F, A, C] =
    fb.flatMap(f)

  def combineK[B](x: Service[F, A, B], y: Service[F, A, B]): Service[F, A, B] =
    x.orElse(y)

  def empty[B]: Service[F, A, B] =
    Service.pass
}

private[http4s] trait ServiceInstances {
  implicit def catsInstancesForHttp4sService[F[_], A, B]
      : Monad[Service[F, A, *]] with MonoidK[Service[F, A, *]] =
    new ServiceInstance[F, A] {}
}
