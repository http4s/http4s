package org.http4s
package server

import org.http4s.websocket.Websocket
import org.http4s.websocket.WebsocketBits.WebSocketFrame

import scalaz.stream.{Exchange, Process, Sink}
import scalaz.concurrent.Task

package object websocket {
  val websocketKey = AttributeKey.http4s[Websocket]("websocket")

  /**
   * Build a response which will accept an HTTP websocket upgrade request and initiate a websocket connection using the
   * supplied exchange to process and respond to websocket messages.
   * @param exchange The read side of the Exchange represents the stream of messages that should be sent to the client
   *                 The write side of the Exchange is a sink to which the framework will push the websocket messages
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
  def WS(exchange: Exchange[WebSocketFrame, WebSocketFrame],
         status: Task[Response] = Response(Status.NotImplemented).withBody("This is a WebSocket route.")): Task[Response] =
    status.map(_.withAttribute(websocketKey, Websocket(exchange)))
}
