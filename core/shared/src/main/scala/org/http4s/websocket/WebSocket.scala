/*
 * Copyright 2013 http4s.org
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

package org.http4s.websocket

import cats.~>
import fs2._

import scala.concurrent.duration.FiniteDuration

private[http4s] case class AutoPingSeparatePipe[F[_]](
    every: FiniteDuration,
    frame: WebSocketFrame.Ping,
    sendWithoutAutoPing: Stream[F, WebSocketFrame],
)

private[http4s] case class AutoPingCombinedPipe[F[_]](
    every: FiniteDuration,
    frame: WebSocketFrame.Ping,
    receiveSendWithoutAutoPing: Pipe[F, WebSocketFrame, WebSocketFrame],
)

private[http4s] sealed trait WebSocket[F[_]] { outer =>
  def onClose: F[Unit]

  def imapK[G[_]](fk: F ~> G)(gk: G ~> F): WebSocket[G]

}

private[http4s] final case class WebSocketSeparatePipe[F[_]](
    send: Stream[F, WebSocketFrame],
    receive: Pipe[F, WebSocketFrame, Unit],
    onClose: F[Unit],
)(autoPingSeparatePipe: Option[AutoPingSeparatePipe[F]])
    extends WebSocket[F] {

  def autoPing: Option[AutoPingSeparatePipe[F]] = autoPingSeparatePipe

  def imapK[G[_]](fk: F ~> G)(gk: G ~> F): WebSocket[G] =
    WebSocketSeparatePipe(
      send.translate(fk),
      (sg: Stream[G, WebSocketFrame]) => receive(sg.translate(gk)).translate(fk),
      fk(onClose),
    )(
      autoPingSeparatePipe.map(x =>
        x.copy(sendWithoutAutoPing = x.sendWithoutAutoPing.translate(fk))
      )
    )
}

private[http4s] final case class WebSocketCombinedPipe[F[_]](
    receiveSend: Pipe[F, WebSocketFrame, WebSocketFrame],
    onClose: F[Unit],
)(autoPingCombinedPipe: Option[AutoPingCombinedPipe[F]])
    extends WebSocket[F] {

  def autoPing: Option[AutoPingCombinedPipe[F]] = autoPingCombinedPipe

  def imapK[G[_]](fk: F ~> G)(gk: G ~> F): WebSocketCombinedPipe[G] =
    WebSocketCombinedPipe(
      (sg: Stream[G, WebSocketFrame]) => receiveSend(sg.translate(gk)).translate(fk),
      fk(onClose),
    )(
      autoPingCombinedPipe.map(x =>
        x.copy(receiveSendWithoutAutoPing =
          (sg: Stream[G, WebSocketFrame]) =>
            x.receiveSendWithoutAutoPing(sg.translate(gk)).translate(fk)
        )
      )
    )
}
