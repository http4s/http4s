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
import cats.effect.std.Queue
import cats.implicits._
import fs2.Stream
import org.http4s.HttpApp
import org.http4s.client.websocket.WSClient
import org.http4s.client.websocket.WSConnection
import org.http4s.client.websocket.WSFrame
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.websocket.WebSocketCombinedPipe
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
  )(f: WebSocketBuilder2[F] => HttpApp[F]): F[WSClient[F]] =
    for {
      wsb <- WebSocketBuilder2[F]
      app = f(wsb)
    } yield WSClient[F](respondToPings) { req =>
      Resource
        .eval(app.run(Request[F](method = req.method, uri = req.uri, headers = req.headers)))
        .flatMap { response =>
          response.attributes.lookup(wsb.webSocketKey) match {
            case Some(context) =>
              context.webSocket match {
                case WebSocketSeparatePipe(s, r, c) =>
                  Resource
                    .pure(new WSConnection[F] {

                      def send(wsf: WSFrame): F[Unit] =
                        Stream.eval(wsf.toWebSocketFrame).through(r).compile.drain

                      def sendMany[G[_]: Foldable, A <: WSFrame](wsfs: G[A]): F[Unit] =
                        wsfs.traverse_(send)

                      def receive: F[Option[WSFrame]] =
                        s.map(_.toWSFrame).head.compile.last

                      def subprotocol: Option[String] = context.subprotocol
                    })
                    .onFinalize(c)
                case wscp: WebSocketCombinedPipe[F] =>
                  Resource
                    .liftK(Queue.unbounded[F, WebSocketFrame])
                    .map { queue =>
                      new WSConnection[F] {
                        def send(wsf: WSFrame): F[Unit] =
                          Stream
                            .eval(wsf.toWebSocketFrame)
                            .through(wscp.receiveSend)
                            .evalTap(queue.offer)
                            .compile
                            .drain

                        def sendMany[G[_]: Foldable, A <: WSFrame](wsfs: G[A]): F[Unit] =
                          wsfs.traverse_(send)

                        def receive: F[Option[WSFrame]] =
                          queue.tryTake.map(_.map(_.toWSFrame))

                        def subprotocol: Option[String] = context.subprotocol
                      }

                    }
                    .onFinalize(wscp.onClose)

              }
            case _ =>
              Resource.raiseError[F, WSConnection[F], Throwable](
                new WebSocketClientInitException()
              )
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
      wsf match {
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
