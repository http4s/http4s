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

package org.http4s
package client
package websocket
package middleware

import cats.Foldable
import cats.effect.kernel.Concurrent
import cats.effect.kernel.DeferredSource
import cats.effect.kernel.Resource
import cats.effect.std.Hotswap
import cats.effect.syntax.all._
import cats.syntax.all._

object Reconnect {

  def apply[F[_]](client: WSClientHighLevel[F])(implicit F: Concurrent[F]): WSClientHighLevel[F] =
    new ImplHighLevel(client)

  private def reconnect[F[_], C <: WSConnectionHighLevel[F]](open: Resource[F, C])(implicit
      F: Concurrent[F]
  ) = Hotswap(open.map(_.asRight[Throwable])).flatMap {
    case (hs, Right(conn)) =>
      def loop: F[Unit] = hs.swap(open.attempt).flatMap {
        case Left(_) => F.unit
        case Right(conn) => conn.closeFrame.get *> loop
      }

      (conn.closeFrame.get *> loop).background.as(
        hs.get.evalMap(
          _.toRight[Throwable](new IllegalStateException("No active connection")).flatten.liftTo[F]
        )
      )
    case _ => throw new AssertionError
  }

  private def neverCloseFrame[F[_]](implicit F: Concurrent[F]) =
    new DeferredSource[F, WSFrame.Close] {
      def get = F.never
      def tryGet = F.pure(none)
    }

  private final class ImplHighLevel[F[_]](client: WSClientHighLevel[F])(implicit F: Concurrent[F])
      extends WSClientHighLevel[F] {
    def connectHighLevel(request: WSRequest) =
      reconnect(client.connectHighLevel(request)).map { conn =>
        new WSConnectionHighLevel[F] {
          def closeFrame = neverCloseFrame
          def receive = conn.use(_.receive)
          def send(wsf: WSDataFrame) = conn.use(_.send(wsf))
          def sendMany[G[_]: Foldable, A <: WSDataFrame](wsfs: G[A]) = conn.use(_.sendMany(wsfs))
          def subprotocol = None
        }
      }
  }

}
