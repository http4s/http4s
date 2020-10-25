/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.effect.{Resource, Sync}
import org.http4s.blaze.util.{Cancelable, TickWheelExecutor}

package object blazecore {

  // Like fs2.async.unsafeRunAsync before 1.0.  Convenient for when we
  // have an ExecutionContext but not a Timer.
  private[http4s] def unsafeRunAsync[F[_], A](fa: F[A])(
      f: Either[Throwable, A] => IO[Unit])(implicit F: Effect[F], ec: ExecutionContext): Unit =
    F.runAsync(Async.shift(ec) *> fa)(f).unsafeRunSync()

  private[http4s] def tickWheelResource[F[_]](implicit F: Sync[F]): Resource[F, TickWheelExecutor] =
    Resource(F.delay {
      val s = new TickWheelExecutor()
      (s, F.delay(s.shutdown()))
    })

  private[blazecore] val NoOpCancelable = new Cancelable {
    def cancel() = ()
    override def toString = "no op cancelable"
  }
}
