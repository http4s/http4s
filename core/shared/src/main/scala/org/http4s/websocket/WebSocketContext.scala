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

package org.http4s
package websocket

import cats.Functor
import cats.syntax.all._
import cats.~>
import org.typelevel.ci.CIStringSyntax

import scala.concurrent.duration.FiniteDuration

final case class WebSocketContext[F[_]](
    webSocket: WebSocket[F],
    headers: Headers,
    failureResponse: F[Response[F]],
    autoPing: Option[(FiniteDuration, WebSocketFrame.Ping)],
) {

  def imapK[G[_]: Functor](fk: F ~> G)(gk: G ~> F): WebSocketContext[G] =
    WebSocketContext[G](
      webSocket.imapK(fk)(gk),
      headers,
      fk(failureResponse).map(_.mapK(fk)),
      autoPing,
    )

  def subprotocol: Option[String] =
    headers.get(ci"Sec-WebSocket-Protocol").map(_.head.value)

}
