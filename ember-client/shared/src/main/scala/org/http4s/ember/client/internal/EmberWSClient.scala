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

import cats.MonadThrow
import cats.effect.Async
import cats.effect.implicits._
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.effect.std.SecureRandom
import cats.syntax.all._
import fs2.concurrent.Channel
import org.http4s.Request
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.websocket.WSClient
import org.http4s.client.websocket.WSConnection
import org.http4s.client.websocket.WSFrame
import org.http4s.ember.client.internal.WebSocketHelpers._
import org.http4s.ember.core.WebSocketHelpers._
import org.http4s.headers.`Sec-WebSocket-Key`
import org.http4s.websocket.WebSocketFrame
import scodec.bits.ByteVector

import java.util.Base64

private[client] object EmberWSClient {
  def apply[F[_]](
      emberClient: Client[F]
  )(implicit F: Async[F]): F[WSClient[F]] =
    SecureRandom.javaSecuritySecureRandom[F].map { random =>
      WSClient[F](respondToPings = false) { wsRequest =>
        for {
          randomByteArray <- Resource.eval(random.nextBytes(16))

          uriScheme = wsRequest.uri.scheme.map(scheme =>
            scheme.value match {
              case "wss" => Uri.Scheme.https
              case "ws" => Uri.Scheme.http
              case _ => scheme
            }
          )

          httpWSRequest = Request[F]()
            .withUri(wsRequest.uri.copy(uriScheme))
            .withHeaders(
              Headers(
                upgradeWebSocket,
                connectionUpgrade,
                supportedWebSocketVersionHeader,
                new `Sec-WebSocket-Key`(ByteVector(Base64.getEncoder().encode(randomByteArray))),
              )
            )
            .withMethod(Method.GET)

          socketOption <- getSocket(emberClient, httpWSRequest)
          socket <- socketOption.liftTo[F](new RuntimeException("Not an Ember client")).toResource

          closeFrameDeffered <- F.deferred[WebSocketFrame.Close].toResource

          clientReceiveQueue <- Queue.bounded[F, WebSocketFrame](100).toResource
          clientSendChannel <- Channel.bounded[F, WebSocketFrame](100).toResource

          _ <- socket.reads
            .through(decodeFrames(true))
            .foreach {
              case f @ WebSocketFrame.Close(_) =>
                closeFrameDeffered.complete(f).ifM(clientReceiveQueue.offer(f), F.unit)
              case f =>
                closeFrameDeffered.tryGet.flatMap { x =>
                  if (x.isDefined) F.unit else clientReceiveQueue.offer(f)
                }
            }
            .compile
            .drain
            .background

          sendingFinished <- clientSendChannel.stream
            .foreach(f => frameToBytes(f, true).traverse_(c => socket.write(c)))
            .compile
            .drain
            .background

          _ <- Resource.onFinalize {
            MonadThrow[F]
              .fromEither(WebSocketFrame.Close(1000, "Connection automatically closed"))
              .flatMap(clientSendChannel.closeWithElement(_)) *> sendingFinished.void
          }
        } yield new WSConnection[F] {
          def receive: F[Option[WSFrame]] = clientReceiveQueue.take.flatMap {
            case f @ WebSocketFrame.Close(_) =>
              closeChannelWithCloseFrame(clientSendChannel).as(toWSFrame(f).some)
            case f =>
              toWSFrame(f).some.pure[F]
          }
          def send(wsf: WSFrame): F[Unit] =
            toWebSocketFrame(wsf).flatMap {
              case WebSocketFrame.Close(_) =>
                closeChannelWithCloseFrame(clientSendChannel)
              case f =>
                clientSendChannel.send(f).void
            }
          def sendMany[G[_], A <: WSFrame](wsfs: G[A])(implicit
              evidence$1: cats.Foldable[G]
          ): F[Unit] = wsfs.traverse_(send(_))
          def subprotocol: Option[String] = ???
        }
      }
    }
}
