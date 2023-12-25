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
package server.websocket

import cats.Applicative
import cats.syntax.all._
import fs2.Pipe
import fs2.Stream
import org.http4s.websocket.WebSocketFrame

import scala.concurrent.duration.FiniteDuration

/** Build a response which will accept an HTTP websocket upgrade request and initiate a websocket connection using the
  * supplied exchange to process and respond to websocket messages.
  * @param headers Handshake response headers, such as such as:Sec-WebSocket-Protocol.
  * @param onNonWebSocketRequest The status code to return to a client making a non-websocket HTTP request to this route.
  *                              default: NotImplemented
  * @param onHandshakeFailure The status code to return when failing to handle a websocket HTTP request to this route.
  *                           default: BadRequest
  * autoPing: Option[(FiniteDuration, WebSocketFrame.Ping)],
  */
@deprecated(
  "Relies on an unsafe cast; instead obtain a WebSocketBuilder2 via .withHttpWebSocketApp on your server builder",
  "0.23.5",
)
final case class WebSocketBuilder[F[_]: Applicative](
    headers: Headers,
    onNonWebSocketRequest: F[Response[F]],
    onHandshakeFailure: F[Response[F]],
    onClose: F[Unit],
    filterPingPongs: Boolean,
    autoPing: Option[(FiniteDuration, WebSocketFrame.Ping)],
) {

  private lazy val delegate: WebSocketBuilder2[F] =
    WebSocketBuilder2(websocketKey[F])
      .withHeaders(headers)
      .withOnNonWebSocketRequest(onNonWebSocketRequest)
      .withOnHandshakeFailure(onHandshakeFailure)
      .withOnClose(onClose)
      .withFilterPingPongs(filterPingPongs)
      .withAutoPing(autoPing)

  /** @param sendReceive The send-receive stream represents transforming of incoming messages to outgoing for a single websocket
    *                    Once the stream have terminated, the server will initiate a close of the websocket connection.
    *                    As defined in the websocket specification, this means the server
    *                    will send a CloseFrame to the client and wait for a CloseFrame in response before closing the
    *                    connection, this ensures that no messages are lost in flight. The server will shutdown the
    *                    connection when it receives the `CloseFrame` message back from the client. The connection will also
    *                    be closed if the client does not respond with a `CloseFrame` after some reasonable amount of
    *                    time.
    *                    Another way of closing the connection is by emitting a `CloseFrame` in the stream of messages
    *                    heading to the client. This method allows one to attach a message to the `CloseFrame` as defined
    *                    by the websocket protocol.
    *                    Unfortunately the current implementation does not quite respect the description above, it violates
    *                    the websocket protocol by terminating the connection immediately upon reception
    *                    of a `CloseFrame`. This bug will be addressed soon in an upcoming release and this message will be
    *                    removed.
    *                    Currently, there is no way for the server to be notified when the connection is closed, neither in
    *                    the case of a normal disconnection such as a Close handshake or due to a connection error. There
    *                    are plans to address this limitation in the future.
    * @return
    */
  def build(sendReceive: Pipe[F, WebSocketFrame, WebSocketFrame]): F[Response[F]] =
    delegate.build(sendReceive)

  /** @param send     The send side of the Exchange represents the outgoing stream of messages that should be sent to the client
    * @param receive  The receive side of the Exchange is a sink to which the framework will push the incoming websocket messages
    *                 Once both streams have terminated, the server will initiate a close of the websocket connection.
    *                 As defined in the websocket specification, this means the server
    *                 will send a CloseFrame to the client and wait for a CloseFrame in response before closing the
    *                 connection, this ensures that no messages are lost in flight. The server will shutdown the
    *                 connection when it receives the `CloseFrame` message back from the client. The connection will also
    *                 be closed if the client does not respond with a `CloseFrame` after some reasonable amount of
    *                 time.
    *                 Another way of closing the connection is by emitting a `CloseFrame` in the stream of messages
    *                 heading to the client. This method allows one to attach a message to the `CloseFrame` as defined
    *                 by the websocket protocol.
    *                 Unfortunately the current implementation does not quite respect the description above, it violates
    *                 the websocket protocol by terminating the connection immediately upon reception
    *                 of a `CloseFrame`. This bug will be addressed soon in an upcoming release and this message will be
    *                 removed.
    *                 Currently, there is no way for the server to be notified when the connection is closed, neither in
    *                 the case of a normal disconnection such as a Close handshake or due to a connection error. There
    *                 are plans to address this limitation in the future.
    * @return
    */
  def build(
      send: Stream[F, WebSocketFrame],
      receive: Pipe[F, WebSocketFrame, Unit],
  ): F[Response[F]] =
    delegate.build(send, receive)

}

@deprecated(
  "Relies on an unsafe cast; instead obtain a WebSocketBuilder2 via .withHttpWebSocketApp on your server builder",
  "0.23.5",
)
object WebSocketBuilder {
  @deprecated(
    "Relies on an unsafe cast; instead obtain a WebSocketBuilder2 via .withHttpWebSocketApp on your server builder",
    "0.23.5",
  )
  def apply[F[_]: Applicative]: WebSocketBuilder[F] =
    new WebSocketBuilder[F](
      headers = Headers.empty,
      onNonWebSocketRequest =
        Response[F](Status.NotImplemented).withEntity("This is a WebSocket route.").pure[F],
      onHandshakeFailure =
        Response[F](Status.BadRequest).withEntity("WebSocket handshake failed.").pure[F],
      onClose = Applicative[F].unit,
      filterPingPongs = true,
      autoPing = None,
    )
}
