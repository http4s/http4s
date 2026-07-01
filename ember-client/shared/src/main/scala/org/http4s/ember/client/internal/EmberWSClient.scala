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
import cats.data.EitherT
import cats.effect.Async
import cats.effect.implicits._
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.effect.std.SecureRandom
import cats.syntax.all._
import fs2.Stream
import fs2.concurrent.Channel
import org.http4s.Request
import org.http4s._
import org.http4s.client.Client
import org.http4s.client.websocket.WSClient
import org.http4s.client.websocket.WSConnection
import org.http4s.client.websocket.WSFrame
import org.http4s.ember.client.internal.WebSocketHelpers._
import org.http4s.ember.core.WebSocketHelpers._
import org.http4s.ember.core.h2.H2Keys.WebSocketUpgradeIdentifier
import org.http4s.headers.`Sec-WebSocket-Key`
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrameDefragmenter.defragFragment
import scodec.bits.ByteVector

private[client] object EmberWSClient {
  def apply[F[_]](
      emberClient: Client[F]
  )(implicit F: Async[F]): F[WSClient[F]] =
    SecureRandom.javaSecuritySecureRandom[F].map { random =>
      WSClient[F](respondToPings = true) { wsRequest =>
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
            .withHeaders(wsRequest.headers)
            .putHeaders(
              Headers(
                upgradeWebSocket,
                connectionUpgrade,
                supportedWebSocketVersionHeader,
                new `Sec-WebSocket-Key`(ByteVector(randomByteArray)),
              )
            )
            .withMethod(Method.GET)
            .withAttribute(WebSocketUpgradeIdentifier, ())

          wsConnectionOption <- getSocket(emberClient, httpWSRequest)
          wsConnection <- wsConnectionOption
            .liftTo[F](new RuntimeException("Not an Ember client"))
            .toResource

          closeFrameDeferred <- F.deferred[WebSocketFrame.Close].toResource

          clientReceiveQueue <- Queue.bounded[F, Option[WebSocketFrame]](100).toResource
          clientSendChannel <- Channel.bounded[F, WebSocketFrame](100).toResource

          // Bytes the HTTP client already read off the socket past the 101 response
          // (the start of the WebSocket stream) must be replayed before reading more,
          // otherwise frames the server sent immediately after the handshake are lost.
          _ <- (Stream.chunk(wsConnection.leftover) ++ wsConnection.socket.reads)
            .through(decodeFrames(true))
            .through(defragFragment)
            .foreach {
              case f @ WebSocketFrame.Close(_) =>
                closeFrameDeferred
                  .complete(f)
                  .ifM(
                    clientReceiveQueue.offer(Some(f)) *> clientReceiveQueue.offer(None),
                    F.unit,
                  )
              case f =>
                closeFrameDeferred.tryGet.flatMap { x =>
                  if (x.isDefined) F.unit else clientReceiveQueue.offer(Some(f))
                }
            }
            .compile
            .drain
            .recover { case EndOfStreamError() => () }
            .guarantee(
              closeFrameDeferred.tryGet.flatMap {
                case None =>
                  // Connection closed without a close frame:
                  // synthesize an abnormal-closure frame (RFC 6455 §7.1.5).
                  F.fromEither(WebSocketFrame.Close(1006, "abnormal closure")).flatMap { abnormal =>
                    closeFrameDeferred.complete(abnormal) *>
                      clientReceiveQueue.offer(Some(abnormal)) *>
                      clientReceiveQueue.offer(None)
                  }
                case Some(_) => F.unit
              }
            )
            .background

          sendingFinished <- clientSendChannel.stream
            .foreach(f => frameToBytes(f, true).traverse_(c => wsConnection.socket.write(c)))
            .compile
            .drain
            .background

          _ <- Resource.onFinalize {
            closeFrameDeferred.tryGet
              .flatMap {
                // RFC 6455 §5.5.1: echo the close code received from the server,
                // except 1006, which must never appear on the wire.
                case Some(close) if close.closeCode != 1006 => close.pure[F]
                case _ =>
                  MonadThrow[F]
                    .fromEither(WebSocketFrame.Close(1000, "Connection automatically closed"))
              }
              .flatMap(clientSendChannel.closeWithElement(_)) *> sendingFinished.void
          }
        } yield new WSConnection[F] {
          def receive: F[Option[WSFrame]] =
            clientReceiveQueue.take.map(_.map(toWSFrame))
          def send(wsf: WSFrame): F[Unit] =
            toWebSocketFrame(wsf).flatMap { f =>
              val sent = f match {
                // A close frame must be the last frame sent on the channel.
                case _: WebSocketFrame.Close => clientSendChannel.closeWithElement(f)
                case _ => clientSendChannel.send(f)
              }
              EitherT(sent).getOrRaise(new RuntimeException("Connection already closed"))
            }
          def sendMany[G[_], A <: WSFrame](wsfs: G[A])(implicit
              evidence$1: cats.Foldable[G]
          ): F[Unit] = wsfs.traverse_(send(_))
          def subprotocol: Option[String] =
            wsConnection.subprotocol.map(_.values.head)
        }
      }
    }
}
