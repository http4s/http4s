package org.http4s.instances

import cats.Functor
import cats.arrow.FunctionK
import cats.data.OptionT

trait FunctionKInstances {

  def FToOptionT[F[_]: Functor]: FunctionK[F, OptionT[F, ?]] =
    Lambda[FunctionK[F, OptionT[F, ?]]](OptionT.liftF(_))

}
