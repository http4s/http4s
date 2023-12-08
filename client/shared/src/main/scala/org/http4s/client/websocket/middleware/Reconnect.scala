/*
 * Copyright 2014 http4s.org
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

package org.http4s.client.websocket
package middleware

import cats.Foldable
import cats.effect.Concurrent
import cats.effect.Resource
import cats.effect.kernel.DeferredSource
import cats.effect.std.Hotswap
import cats.effect.syntax.all._
import cats.syntax.all._

object Reconnect {

  def apply[F[_]](
      connect: Resource[F, WSConnectionHighLevel[F]]
  )(implicit F: Concurrent[F]): Resource[F, WSConnectionHighLevel[F]] =
    Hotswap(connect.map(_.asRight[Throwable]))
      .flatMap {
        case (hs, Right(conn)) =>
          def loop: F[Unit] = hs.swap(connect.attempt).flatMap {
            case Left(_) => F.unit
            case Right(conn) => conn.closeFrame.get *> loop
          }

          (conn.closeFrame.get *> loop).background.as(
            hs.get.evalMap(
              _.toRight[Throwable](new IllegalStateException("No active connection")).flatten
                .liftTo[F]
            )
          )
        case _ => throw new AssertionError
      }
      .map { conn =>
        new WSConnectionHighLevel[F] {
          def receive = conn.use(_.receive).flatMap {
            case None => F.cede *> receive
            case some => F.pure(some)
          }
          def send(wsf: WSDataFrame) = conn.use(_.send(wsf))
          def sendMany[G[_]: Foldable, A <: WSDataFrame](wsfs: G[A]) = conn.use(_.sendMany(wsfs))
          def subprotocol = None
          def closeFrame = new DeferredSource[F, WSFrame.Close] {
            def get = F.never
            def tryGet = F.pure(none)
          }
        }
      }

}
