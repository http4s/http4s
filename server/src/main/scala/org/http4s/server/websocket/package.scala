/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server

import org.http4s.websocket.WebSocketContext
import io.chrisdavenport.vault._
import cats.effect._

package object websocket {
  private[this] object Keys {
    val WebSocket: Key[Any] = Key.newKey[IO, Any].unsafeRunSync()
  }

  def websocketKey[F[_]]: Key[WebSocketContext[F]] =
    Keys.WebSocket.asInstanceOf[Key[WebSocketContext[F]]]
}
