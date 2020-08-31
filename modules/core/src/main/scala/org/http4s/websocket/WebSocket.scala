/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
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
