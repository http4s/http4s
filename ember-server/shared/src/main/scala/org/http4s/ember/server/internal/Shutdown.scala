/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.server.internal

import _root_.org.typelevel.log4cats.Logger
import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import fs2.Stream

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration

private[server] abstract class Shutdown[F[_]] {
  def await: F[Unit]
  def signal: F[Unit]
  def newConnection: F[Unit]
  def removeConnection: F[Unit]

  def trackConnection: Stream[F, Unit] =
    Stream.bracket(newConnection)(_ => removeConnection)
}

private[server] object Shutdown {

  def apply[F[_]](timeout: Duration, logger: Logger[F])(implicit F: Temporal[F]): F[Shutdown[F]] =
    timeout match {
      case fi: FiniteDuration =>
        if (fi.length == 0) immediateShutdown else timedShutdown(timeout, logger)
      case _ => timedShutdown(timeout, logger)
    }

  private def timedShutdown[F[_]](timeout: Duration, logger: Logger[F])(implicit
      F: Temporal[F]
  ): F[Shutdown[F]] = {
    case class State(isShutdown: Boolean, active: Int)

    for {
      unblockStart <- Deferred[F, Unit]
      unblockFinish <- Deferred[F, Unit]
      state <- Ref.of[F, State](State(isShutdown = false, 0))
    } yield new Shutdown[F] {
      override val await: F[Unit] =
        unblockStart
          .complete(())
          .flatMap { _ =>
            state.modify { case s @ State(_, active) =>
              val fa = if (active == 0) {
                F.unit
              } else {
                timeout match {
                  case fi: FiniteDuration =>
                    logger.info(
                      s"The server's graceful shutdown is prevented by ${active} connections. " +
                        s"Shutdown will be enforced in ${fi.toMillis} milliseconds. To reduce this timeout, use " +
                        ".withShutdownTimeout(<duration>) method on the EmberServerBuilder"
                    ) *>
                      unblockFinish.get.timeoutTo(fi, F.unit)
                  case _ => unblockFinish.get
                }
              }
              s.copy(isShutdown = true) -> fa
            }
          }
          .uncancelable
          .flatten

      override val signal: F[Unit] =
        unblockStart.get

      override val newConnection: F[Unit] =
        state.update { s =>
          s.copy(active = s.active + 1)
        }

      override val removeConnection: F[Unit] =
        state
          .modify { case s @ State(isShutdown, active) =>
            val conns = active - 1
            if (isShutdown && conns <= 0) {
              s.copy(active = conns) -> unblockFinish.complete(()).void
            } else {
              s.copy(active = conns) -> F.unit
            }
          }
          .flatten
          .uncancelable
    }
  }

  private def immediateShutdown[F[_]](implicit F: Concurrent[F]): F[Shutdown[F]] =
    Deferred[F, Unit].map { unblock =>
      new Shutdown[F] {
        override val await: F[Unit] = unblock.complete(()).void
        override val signal: F[Unit] = unblock.get
        override val newConnection: F[Unit] = F.unit
        override val removeConnection: F[Unit] = F.unit
        override val trackConnection: Stream[F, Unit] = Stream.emit(())
      }
    }

}
