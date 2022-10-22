package org.http4s

import cats.effect.kernel.Resource

sealed abstract class Service[F[_], -A, B] { self =>
  def run: A => Resource[F, Option[B]]
}

object Service {
  private[Service] final case class Run[F[_], -A, B](run: A => Resource[F, Option[B]])

  def apply[F[_], A, B](run: A => Resource[F, Option[B]]) =
    Run(run)
}
