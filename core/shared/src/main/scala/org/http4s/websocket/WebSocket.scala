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

import fs2._

private[http4s] sealed trait WebSocket[F[_]] {
  def onClose: F[Unit]
}

private[http4s] final case class WebSocketSeparatePipe[F[_]](
    send: Stream[F, WebSocketFrame],
    receive: Pipe[F, WebSocketFrame, Unit],
    onClose: F[Unit]
) extends WebSocket[F]

private[http4s] final case class WebSocketCombinedPipe[F[_]](
    receiveSend: Pipe[F, WebSocketFrame, WebSocketFrame],
    onClose: F[Unit]
) extends WebSocket[F]
