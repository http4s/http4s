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
package testkit

import cats.Applicative
import cats.Foldable
import cats.MonadThrow
import cats.effect.Concurrent
import cats.effect.Resource
import cats.effect.implicits._
import cats.effect.std.Queue
import cats.implicits._
import fs2.Stream
import org.http4s.HttpApp
import org.http4s.client.websocket.WSClient
import org.http4s.client.websocket.WSConnection
import org.http4s.client.websocket.WSFrame
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketCombinedPipe
import org.http4s.websocket.WebSocketContext
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketSeparatePipe

object WSTestClient {

  def fromHttpWebSocketApp[F[_]: Concurrent](
      f: WebSocketBuilder2[F] => HttpApp[F]
  ): F[WSClient[F]] =
    fromHttpWebSocketApp(respondToPings = true)(f)

  /** Creates a WSClient from the specified [[HttpApp]].
    * [[org.http4s.server.websocket.WebSocketBuilder2]] is used for specifying the WebSocket connection.
    * Useful for generating pre-determined responses for requests in testing.
    *
    * @param respondToPings if true, the client will respond to ping frames with a pong frame.
    * @param f a function that takes a [[org.http4s.server.websocket.WebSocketBuilder2]] and returns an [[HttpApp]].
    */
  def fromHttpWebSocketApp[F[_]: Concurrent](
      respondToPings: Boolean
  )(f: WebSocketBuilder2[F] => HttpApp[F]): F[WSClient[F]] = {

    def processSeparatedSocket(
        socket: WebSocketSeparatePipe[F],
        subProtocol: Option[String],
    ): Resource[F, WSConnection[F]] =
      for {
        clientReceiveQueue <- Queue.bounded[F, Option[WebSocketFrame]](1).toResource
        _ <- socket.send.enqueueNoneTerminated(clientReceiveQueue).compile.drain.background

        clientSendQueue <- Queue.synchronous[F, Option[WebSocketFrame]].toResource
        consumer = Stream
          .fromQueueNoneTerminated[F, WebSocketFrame](clientSendQueue)
          .through(socket.receive)
          .compile
          .drain

        _ <- Resource.make(consumer.start)(f => clientSendQueue.offer(None).guarantee(f.join.void))

        _ <- Resource.onFinalize(socket.onClose)

      } yield new WSConnection[F] {
        def send(wsf: WSFrame): F[Unit] =
          wsf.toWebSocketFrame.flatMap(f => clientSendQueue.offer(f.some))

        def sendMany[G[_]: Foldable, A <: WSFrame](wsfs: G[A]): F[Unit] =
          wsfs.traverse_(send)

        def receive: F[Option[WSFrame]] =
          clientReceiveQueue.take.map(_.map(_.toWSFrame))

        def subprotocol: Option[String] = subProtocol
      }

    def processCombinedSocket(
        socket: WebSocketCombinedPipe[F],
        subProtocol: Option[String],
    ): Resource[F, WSConnection[F]] =
      for {

        clientSendQueue <- Queue.synchronous[F, Option[WebSocketFrame]].toResource
        clientReceiveQueue <- Queue.bounded[F, Option[WebSocketFrame]](1).toResource
        receiveSend = Stream
          .fromQueueNoneTerminated[F, WebSocketFrame](clientSendQueue)
          .through(socket.receiveSend)
          .enqueueNoneTerminated(clientReceiveQueue)
          .compile
          .drain

        _ <- Resource.make(receiveSend.start)(f =>
          clientReceiveQueue.tryTake *> clientSendQueue.offer(None).guarantee(f.join.void)
        )

        _ <- Resource.onFinalize(socket.onClose)

      } yield new WSConnection[F] {
        def send(wsf: WSFrame): F[Unit] =
          wsf.toWebSocketFrame.flatMap(f => clientSendQueue.offer(f.some))

        def sendMany[G[_]: Foldable, A <: WSFrame](wsfs: G[A]): F[Unit] =
          wsfs.traverse_(send)

        def receive: F[Option[WSFrame]] =
          clientReceiveQueue.take.map(_.map(_.toWSFrame))

        def subprotocol: Option[String] = subProtocol
      }

    for {
      wsb <- WebSocketBuilder2[F]
      app = f(wsb)
    } yield WSClient[F](respondToPings) { req =>
      Resource
        .eval(app.run(Request[F](method = req.method, uri = req.uri, headers = req.headers)))
        .flatMap { response =>
          response.attributes.lookup(wsb.webSocketKey) match {
            case Some(c @ WebSocketContext(webSocket: WebSocketSeparatePipe[F], _, _)) =>
              processSeparatedSocket(webSocket, c.subprotocol)
            case Some(c @ WebSocketContext(webSocket: WebSocketCombinedPipe[F], _, _)) =>
              processCombinedSocket(webSocket, c.subprotocol)
            case _ =>
              Resource.raiseError[F, WSConnection[F], Throwable](new WebSocketClientInitException())
          }

        }

    }
  }

  implicit private[http4s] class WsFrameOps[F[_]: Concurrent](val wsFrame: WSFrame) {
    def toWebSocketFrame: F[WebSocketFrame] =
      wsFrame match {
        case WSFrame.Close(code, reason) =>
          MonadThrow[F].fromEither(WebSocketFrame.Close(code, reason))
        case WSFrame.Ping(data) => Applicative[F].pure(WebSocketFrame.Ping(data))
        case WSFrame.Pong(data) => Applicative[F].pure(WebSocketFrame.Pong(data))
        case WSFrame.Text(data, last) => Applicative[F].pure(WebSocketFrame.Text(data, last))
        case WSFrame.Binary(data, last) => Applicative[F].pure(WebSocketFrame.Binary(data, last))
      }
  }

  implicit private[http4s] class WebSocketFrameOps(val wsf: WebSocketFrame) {
    def toWSFrame: WSFrame =
      (wsf: @unchecked) match {
        case c: WebSocketFrame.Close => WSFrame.Close(c.closeCode, c.reason)
        case WebSocketFrame.Ping(data) => WSFrame.Ping(data)
        case WebSocketFrame.Pong(data) => WSFrame.Pong(data)
        case WebSocketFrame.Text(data, last) => WSFrame.Text(data, last)
        case WebSocketFrame.Binary(data, last) => WSFrame.Binary(data, last)
      }
  }

  class WebSocketClientInitException extends RuntimeException {
    def message: String = "WebSocket client initialization failed"
  }

}
