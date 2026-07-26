package org.http4s.server.websocket

import cats._
import cats.implicits._
import fs2._
import org.http4s.websocket.WebsocketBits.WebSocketFrame
import org.http4s.websocket.{WebSocketContext, Websocket}
import org.http4s.{AttributeEntry, Headers, Response, Status}

/** A low level ws builder
  *
  * Build a response which will accept an HTTP websocket upgrade request and initiate a websocket connection using the
  * supplied exchange to process and respond to websocket messages.
  * @param send     The send side of the Exchange represents the outgoing stream of messages that should be sent to the client
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
  * @param headers Handshake response headers, such as such as:Sec-WebSocket-Protocol.
  * @param onNonWebSocketRequest The status code to return to a client making a non-websocket HTTP request to this route.
  *                              default: NotImplemented
  * @param onHandshakeFailure The status code to return when failing to handle a websocket HTTP request to this route.
  *                           default: BadRequest
  */
case class LLWebSocketBuilder[F[_]](
    send: Stream[F, WebSocketFrame],
    receive: Sink[F, WebSocketFrame],
    headers: Headers,
    onNonWebSocketRequest: F[Response[F]],
    onHandshakeFailure: F[Response[F]])
object LLWebSocketBuilder {

  class Builder[F[_]: Applicative] {
    def build(
        send: Stream[F, WebSocketFrame],
        receive: Sink[F, WebSocketFrame],
        headers: Headers = Headers.empty,
        onNonWebSocketRequest: F[Response[F]] =
          Response[F](Status.NotImplemented).withEntity("This is a WebSocket route.").pure[F],
        onHandshakeFailure: F[Response[F]] = Response[F](Status.BadRequest)
          .withEntity("WebSocket handshake failed.")
          .pure[F],
        onClose: F[Unit] = Applicative[F].unit): F[Response[F]] =
      LLWebSocketBuilder(send, receive, headers, onNonWebSocketRequest, onHandshakeFailure).onNonWebSocketRequest
        .map(
          _.withAttribute(
            AttributeEntry(
              websocketKey[F],
              WebSocketContext(Websocket(send, receive, onClose), headers, onHandshakeFailure))))
  }
  def apply[F[_]: Applicative]: Builder[F] = new Builder[F]
}
