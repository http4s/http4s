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

import cats.syntax.all._
import cats.effect._
import cats.effect.implicits._
import cats.effect.concurrent._

import scala.concurrent.duration.{Duration, FiniteDuration}

private[server] abstract class Shutdown[F[_]] {
  // TODO: timeout could just be done by the caller
  def await(timeout: Duration): F[Unit]
  def newConnection: F[Unit]
  def removeConnection: F[Unit]
}

private[server] object Shutdown {

  def apply[F[_]](implicit F: Concurrent[F], timer: Timer[F]): F[Shutdown[F]] = {
    case class State(isShutdown: Boolean, activeConnections: Int, unblock: Deferred[F, Unit])

    for {
      unblock <- Deferred[F, Unit]
      state <- Ref.of[F, State](State(false, 0, unblock))
    } yield new Shutdown[F] {
      // TODO: Deal with cancellation
      override def await(timeout: Duration): F[Unit] =
        state.modify {
          case s @ State(_, activeConnections, unblock) =>
            if (activeConnections == 0) {
              s.copy(isShutdown = true) -> F.unit
            } else {
              val fa = timeout match {
                case fi: FiniteDuration => unblock.get.timeout(fi)
                case _ => unblock.get
              }
              s.copy(isShutdown = true) -> fa
            }
        }.flatten

      override def newConnection: F[Unit] =
        state.update { s =>
          s.copy(activeConnections = s.activeConnections + 1)
        }

      override def removeConnection: F[Unit] =
        state.modify {
          case s @ State(isShutdown, activeConnections, unblock) =>
            val nextConnections = activeConnections - 1
            if (isShutdown && nextConnections == 0) {
              State(true, 0, unblock) -> unblock.complete(())
            } else {
              s.copy(activeConnections = nextConnections) -> F.unit
            }
        }.flatten
    }
  }

}
