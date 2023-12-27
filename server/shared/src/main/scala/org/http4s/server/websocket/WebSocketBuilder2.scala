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

import cats.effect.kernel._
import cats.effect.syntax.all._
import cats.syntax.all._
import cats.{Applicative, ~>}
import fs2.{Pipe, Stream}
import org.http4s.websocket.WebSocketFrameDefragmenter.defragFragment
import org.http4s.websocket._
import org.typelevel.vault.Key

import scala.concurrent.duration.FiniteDuration

/** Build a response which will accept an HTTP websocket upgrade request and initiate a websocket connection using the
  * supplied exchange to process and respond to websocket messages.
  * @param headers Handshake response headers, such as such as:Sec-WebSocket-Protocol.
  * @param onNonWebSocketRequest The status code to return to a client making a non-websocket HTTP request to this route.
  *                              default: NotImplemented
  * @param onHandshakeFailure The status code to return when failing to handle a websocket HTTP request to this route.
  *                           default: BadRequest
  * @param defragFrame Indicates whether incoming WebSocket frames are defragmented or not.
  *                    If this flag is true, frames fragmented according to rfc6455 will arrive defragged
  *                    in the user-provided recieve handler. Note that this defrag feature is '''true''' by default.
  *                    To prevent WebSocketBuilder2 from handling defrag, you must explicitly call withDefragment(false).
  *                    For more information on defrag processing, see the WebSocketFrameDefragmenter comment.
  *                    default: true
  * @param autoPing If `Some`, send the given Websocket `Ping` frame at the given interval.
  *                    If `None`, do not automatically send pings.
  */
sealed abstract class WebSocketBuilder2[F[_]: Applicative] private (
    headers: Headers,
    onNonWebSocketRequest: F[Response[F]],
    onHandshakeFailure: F[Response[F]],
    onClose: F[Unit],
    filterPingPongs: Boolean,
    defragFrame: Boolean,
    private[http4s] val webSocketKey: Key[WebSocketContext[F]],
    autoPing: Option[(FiniteDuration, WebSocketFrame.Ping, Temporal[F])],
) {
  import WebSocketBuilder2.impl

  // required for binary compatibility
  def this(
      headers: Headers,
      onNonWebSocketRequest: F[Response[F]],
      onHandshakeFailure: F[Response[F]],
      onClose: F[Unit],
      filterPingPongs: Boolean,
      defragFrame: Boolean,
      webSocketKey: Key[WebSocketContext[F]],
  ) =
    this(
      headers,
      onNonWebSocketRequest,
      onHandshakeFailure,
      onClose,
      filterPingPongs,
      defragFrame,
      webSocketKey,
      None,
    )

  @deprecated(
    "Kept for binary compatiblity. Use the constructor that includes `defragFrame` as an argument",
    "0.23.19",
  )
  def this(
      headers: Headers,
      onNonWebSocketRequest: F[Response[F]],
      onHandshakeFailure: F[Response[F]],
      onClose: F[Unit],
      filterPingPongs: Boolean,
      webSocketKey: Key[WebSocketContext[F]],
  ) =
    this(
      headers = headers,
      onNonWebSocketRequest = onNonWebSocketRequest,
      onHandshakeFailure = onHandshakeFailure,
      onClose = onClose,
      filterPingPongs = filterPingPongs,
      defragFrame = false,
      webSocketKey = webSocketKey,
      autoPing = None,
    )

  private def copy(
      headers: Headers = this.headers,
      onNonWebSocketRequest: F[Response[F]] = this.onNonWebSocketRequest,
      onHandshakeFailure: F[Response[F]] = this.onHandshakeFailure,
      onClose: F[Unit] = this.onClose,
      filterPingPongs: Boolean = this.filterPingPongs,
      defragFrame: Boolean = this.defragFrame,
      webSocketKey: Key[WebSocketContext[F]] = this.webSocketKey,
      autoPing: Option[(FiniteDuration, WebSocketFrame.Ping, Temporal[F])] = this.autoPing,
  ): WebSocketBuilder2[F] = WebSocketBuilder2.impl[F](
    headers,
    onNonWebSocketRequest,
    onHandshakeFailure,
    onClose,
    filterPingPongs,
    defragFrame,
    webSocketKey,
    autoPing,
  )

  def withHeaders(headers: Headers): WebSocketBuilder2[F] =
    copy(headers = headers)

  def withOnNonWebSocketRequest(onNonWebSocketRequest: F[Response[F]]): WebSocketBuilder2[F] =
    copy(onNonWebSocketRequest = onNonWebSocketRequest)

  def withOnHandshakeFailure(onHandshakeFailure: F[Response[F]]): WebSocketBuilder2[F] =
    copy(onHandshakeFailure = onHandshakeFailure)

  def withOnClose(onClose: F[Unit]): WebSocketBuilder2[F] =
    copy(onClose = onClose)

  def withFilterPingPongs(filterPingPongs: Boolean): WebSocketBuilder2[F] =
    copy(filterPingPongs = filterPingPongs)

  def withDefragment(defragFrame: Boolean): WebSocketBuilder2[F] =
    copy(defragFrame = defragFrame)

  def withAutoPing(every: FiniteDuration, frame: WebSocketFrame.Ping)(implicit
      T: Temporal[F]
  ): WebSocketBuilder2[F] =
    copy(autoPing = Some((every, frame, T)))

  def withoutAutoPing: WebSocketBuilder2[F] = copy(autoPing = None)

  /** Transform the parameterized effect from F to G. */
  def imapK[G[_]: Applicative](fk: F ~> G)(gk: G ~> F): WebSocketBuilder2[G] =
    impl[G](
      headers,
      fk(onNonWebSocketRequest).map(_.mapK(fk)),
      fk(onHandshakeFailure).map(_.mapK(fk)),
      fk(onClose),
      filterPingPongs,
      defragFrame,
      webSocketKey.imap(_.imapK(fk)(gk))(_.imapK(gk)(fk)),
      autoPing.map { x =>
        val tf: Temporal[F] = x._3

        x.copy(_3 = new Temporal[G] {
          protected def sleep(time: FiniteDuration): G[Unit] = fk(tf.sleep(time))

          def ref[A](a: A): G[Ref[G, A]] = fk(tf.ref(a).map(_.mapK(fk)))

          def deferred[A]: G[Deferred[G, A]] = fk(tf.deferred[A].map(_.mapK(fk)))

          def start[A](fa: G[A]): G[Fiber[G, Throwable, A]] = fk(
            tf.start(gk(fa))
              .map(f =>
                new Fiber[G, Throwable, A] {
                  def cancel: G[Unit] = fk(f.cancel)

                  def join: G[Outcome[G, Throwable, A]] = fk(f.join.map(_.mapK(fk)))
                }
              )
          )

          def never[A]: G[A] = fk(tf.never)

          def cede: G[Unit] = fk(tf.cede)

          def forceR[A, B](fa: G[A])(fb: G[B]): G[B] = fk(tf.forceR(gk(fa))(gk(fb)))

          def uncancelable[A](body: Poll[G] => G[A]): G[A] =
            fk(tf.uncancelable { pollF =>
              gk(body(new Poll[G] {
                def apply[B](gb: G[B]): G[B] = fk(pollF(gk(gb)))
              }))
            })

          def canceled: G[Unit] = fk(tf.canceled)

          def onCancel[A](fa: G[A], fin: G[Unit]): G[A] = fk(tf.onCancel(gk(fa), gk(fin)))

          def flatMap[A, B](fa: G[A])(f: A => G[B]): G[B] = fk(tf.flatMap(gk(fa))(a => gk(f(a))))

          def tailRecM[A, B](a: A)(f: A => G[Either[A, B]]): G[B] =
            fk(tf.tailRecM(a)(a => gk(f(a))))

          def unique: G[Unique.Token] = fk(tf.unique)

          def raiseError[A](e: Throwable): G[A] = fk(tf.raiseError(e))

          def handleErrorWith[A](fa: G[A])(f: Throwable => G[A]): G[A] =
            fk(tf.handleErrorWith(gk(fa))(ex => gk(f(ex))))

          def monotonic: G[FiniteDuration] = fk(tf.monotonic)

          def realTime: G[FiniteDuration] = fk(tf.realTime)

          def pure[A](x: A): G[A] = fk(tf.pure(x))
        })
      },
    )

  private def buildResponse(webSocket: WebSocket[F]): F[Response[F]] =
    onNonWebSocketRequest
      .map(
        _.withAttribute(
          webSocketKey,
          WebSocketContext(
            webSocket,
            headers,
            onHandshakeFailure,
          ),
        )
      )

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
  def build(sendReceive: Pipe[F, WebSocketFrame, WebSocketFrame]): F[Response[F]] = {

    val finalSendReceive: Pipe[F, WebSocketFrame, WebSocketFrame] =
      (filterPingPongs, defragFrame) match {
        case (true, false) => sendReceive.compose(filterPingPongFrames)
        case (false, false) => sendReceive
        case (true, true) => sendReceive.compose(defragFragment.compose(filterPingPongFrames))
        case (false, true) => sendReceive.compose(defragFragment)
      }

    autoPing match {
      case None => buildResponse(WebSocketCombinedPipe(finalSendReceive, onClose)(None))
      case Some((every, frame, temporal)) =>
        val ping = temporal.pure(frame).delayBy(every)(temporal)
        val pings: Stream[F, WebSocketFrame] = Stream.repeatEval(ping).repeat
        buildResponse(
          WebSocketCombinedPipe(
            (input: Stream[F, WebSocketFrame]) =>
              Stream(finalSendReceive(input), pings).parJoinUnbounded(temporal),
            onClose,
          )(
            Some(AutoPingCombinedPipe(every, frame, finalSendReceive))
          )
        )
    }

  }

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
  ): F[Response[F]] = {

    val finalReceive: Pipe[F, WebSocketFrame, Unit] =
      (filterPingPongs, defragFrame) match {
        case (true, false) => receive.compose(filterPingPongFrames)
        case (false, false) => receive
        case (true, true) => receive.compose(defragFragment.compose(filterPingPongFrames))
        case (false, true) => receive.compose(defragFragment)
      }

    autoPing match {
      case None => buildResponse(WebSocketSeparatePipe(send, finalReceive, onClose)(None))
      case Some((every, frame, temporal)) =>
        val ping = temporal.pure(frame).delayBy(every)(temporal)
        val pings: Stream[F, WebSocketFrame] = Stream.repeatEval(ping).repeat
        buildResponse(
          WebSocketSeparatePipe(
            Stream(send, pings).parJoinUnbounded(temporal),
            finalReceive,
            onClose,
          )(
            Some(AutoPingSeparatePipe(every, frame, send))
          )
        )
    }
  }

  private val isPingPong: WebSocketFrame => Boolean = {
    case _: WebSocketFrame.Ping => true
    case _: WebSocketFrame.Pong => true
    case _ => false
  }

  private def filterPingPongFrames: Pipe[F, WebSocketFrame, WebSocketFrame] = inputStream =>
    inputStream.filterNot(isPingPong)

}

object WebSocketBuilder2 {
  @deprecated(
    "Use the arg-less constructor to create a `WebSocketBuilder2` and access its key with the webSocketKey method",
    "0.23.15",
  )
  private[http4s] def apply[F[_]: Applicative](
      webSocketKey: Key[WebSocketContext[F]]
  ): WebSocketBuilder2[F] =
    withKey(webSocketKey)

  def apply[F[_]: Applicative: Unique]: F[WebSocketBuilder2[F]] =
    Key.newKey[F, WebSocketContext[F]].map(withKey[F])

  private def withKey[F[_]: Applicative](
      webSocketKey: Key[WebSocketContext[F]]
  ): WebSocketBuilder2[F] =
    impl(
      headers = Headers.empty,
      onNonWebSocketRequest =
        Response[F](Status.NotImplemented).withEntity("This is a WebSocket route.").pure[F],
      onHandshakeFailure =
        Response[F](Status.BadRequest).withEntity("WebSocket handshake failed.").pure[F],
      onClose = Applicative[F].unit,
      filterPingPongs = true,
      defragFrame = true,
      webSocketKey = webSocketKey,
      autoPing = None,
    )

  private def impl[F[_]: Applicative](
      headers: Headers,
      onNonWebSocketRequest: F[Response[F]],
      onHandshakeFailure: F[Response[F]],
      onClose: F[Unit],
      filterPingPongs: Boolean,
      defragFrame: Boolean,
      webSocketKey: Key[WebSocketContext[F]],
      autoPing: Option[(FiniteDuration, WebSocketFrame.Ping, Temporal[F])],
  ): WebSocketBuilder2[F] =
    new WebSocketBuilder2[F](
      headers = headers,
      onNonWebSocketRequest = onNonWebSocketRequest,
      onHandshakeFailure = onHandshakeFailure,
      onClose = onClose,
      filterPingPongs = filterPingPongs,
      defragFrame = defragFrame,
      webSocketKey = webSocketKey,
      autoPing = autoPing,
    ) {}
}
