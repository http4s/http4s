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

package org.http4s.ember.client.internal

import cats.effect.Async
import cats.effect.implicits._
import cats.effect.std.Queue
import cats.syntax.all._
import org.http4s.Request
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.websocket.WSClient
import org.http4s.client.websocket.WSConnection
import org.http4s.client.websocket.WSFrame
import org.http4s.ember.client.internal.WebSocketHelpers._
import org.http4s.ember.core.WebSocketHelpers.decodeFrames
import org.http4s.ember.core.WebSocketHelpers.frameToBytes
import org.http4s.websocket.WebSocketFrame

object EmberWSClient {
  def apply[F[_]](
      emberClient: Client[F]
  )(implicit F: Async[F]): WSClient[F] =
    WSClient[F](respondToPings = false) { wsRequest =>
      val httpWSRequest = Request[F]()
        .withUri(wsRequest.uri)
        .withHeaders(wsRequest.headers)
        .withMethod(Method.GET)

      for {
        socketOption <- getSocket(emberClient, httpWSRequest)
        socket <- socketOption.liftTo[F](new RuntimeException("Not an Ember client")).toResource

        closeFrameDeffered <- F.deferred[WebSocketFrame.Close].toResource

        clientReceiveQueue <- Queue.bounded[F, WebSocketFrame](100).toResource
        clientSendQueue <- Queue.bounded[F, WebSocketFrame](100).toResource

        _ <- socket.reads
          .through(decodeFrames(true))
          .foreach {
            case f @ WebSocketFrame.Close(_) =>
              closeFrameDeffered.complete(f) >>
                clientReceiveQueue.offer(f)
            case f =>
              closeFrameDeffered.tryGet.flatMap { x =>
                if (x.isDefined) F.unit else clientReceiveQueue.offer(f)
              }
          }
          .compile
          .drain
          .background

        _ <- clientSendQueue.take
          .flatMap(f => frameToBytes(f, true).traverse_(c => socket.write(c)))
          .foreverM
          .void
          .background
      } yield new WSConnection[F] {
        def receive: F[Option[WSFrame]] = clientReceiveQueue.take.map(toWSFrame(_).some)
        def send(wsf: WSFrame): F[Unit] = toWebSocketFrame(wsf).flatMap(clientSendQueue.offer(_))
        def sendMany[G[_], A <: WSFrame](wsfs: G[A])(implicit
            evidence$1: cats.Foldable[G]
        ): F[Unit] = wsfs.traverse_(send(_))
        def subprotocol: Option[String] = ???
      }
    }
}
