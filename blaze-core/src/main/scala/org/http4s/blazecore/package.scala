package org.http4s

import cats.effect.{Resource, Sync}
import org.http4s.blaze.util.{Cancelable, TickWheelExecutor}

package object blazecore {
  private[http4s] def tickWheelAllocate[F[_]](
      implicit F: Sync[F]): F[(TickWheelExecutor, F[Unit])] =
    F.delay {
      val s = new TickWheelExecutor()
      (s, F.delay(s.shutdown()))
    }

  private[http4s] def tickWheelResource[F[_]](implicit F: Sync[F]): Resource[F, TickWheelExecutor] =
    Resource(tickWheelAllocate)

  private[blazecore] val NoOpCancelable = new Cancelable {
    def cancel() = ()
    override def toString = "no op cancelable"
  }
}
