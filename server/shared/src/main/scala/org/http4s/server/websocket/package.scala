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
package server

import cats.effect._
import org.http4s.websocket.WebSocketContext
import org.typelevel.vault.Key

package object websocket {
  private[this] object Keys {
    val WebSocket: Key[Any] = Key.newKey[SyncIO, Any].unsafeRunSync()
  }

  @deprecated(
    "Performs an unsafe cast. Should be passed in from the backend builder that knows what F is.",
    "0.23.5",
  )
  def websocketKey[F[_]]: Key[WebSocketContext[F]] =
    Keys.WebSocket.asInstanceOf[Key[WebSocketContext[F]]]
}
