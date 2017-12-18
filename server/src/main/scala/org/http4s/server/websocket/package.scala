package org.http4s
package server

import cats._
import cats.implicits._
import fs2._
import org.http4s.websocket.Websocket
import org.http4s.websocket.WebsocketBits.WebSocketFrame

package object websocket {
  private[this] object Keys {
    val WebSocket: AttributeKey[Any] = AttributeKey[Any]
  }
  def websocketKey[F[_]]: AttributeKey[Websocket[F]] =
    Keys.WebSocket.asInstanceOf[AttributeKey[Websocket[F]]]

  /**
    * Build a response which will accept an HTTP websocket upgrade request and initiate a websocket connection using the
    * supplied exchange to process and respond to websocket messages.
    * @param send     The send side of the Exchange represents the stream of messages that should be sent to the client
    * @param receive  The receive side of the Exchange is a sink to which the framework will push the websocket messages
    *                 received from the client.
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
    * @param status The status code to return to a client making a non-websocket HTTP request to this route
    */
  def WS[F[_]](
      send: Stream[F, WebSocketFrame],
      receive: Sink[F, WebSocketFrame],
      status: F[Response[F]])(implicit F: Functor[F]): F[Response[F]] =
    status.map(_.withAttribute(AttributeEntry(websocketKey[F], Websocket(send, receive))))

  def WS[F[_]](send: Stream[F, WebSocketFrame], receive: Sink[F, WebSocketFrame])(
      implicit F: Monad[F],
      W: EntityEncoder[F, String]): F[Response[F]] =
    WS(send, receive, Response[F](Status.NotImplemented).withBody("This is a WebSocket route."))
}
