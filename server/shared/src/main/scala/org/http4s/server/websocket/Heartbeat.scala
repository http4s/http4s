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

import cats.effect.kernel.Temporal
import cats.effect.std.Queue
import cats.syntax.all._
import cats.~>
import fs2.Pipe
import fs2.Stream
import org.http4s.websocket.WebSocket
import org.http4s.websocket.WebSocketCombinedPipe
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketSeparatePipe

import scala.concurrent.duration.FiniteDuration

/** The heartbeat of a single connection.
  *
  * @param withPings  Merges the pings into the outgoing frames, and terminates the stream with a
  *                   close frame when a pong is overdue.
  * @param isAnswer   Whether an incoming frame answers a ping of this heartbeat.
  * @param answered   Settles the heartbeat, run when an answer comes in.
  */
private[websocket] final class Heartbeat[F[_]](
    withPings: Pipe[F, WebSocketFrame, WebSocketFrame],
    isAnswer: WebSocketFrame => Boolean,
    answered: F[Unit],
) {

  /** Pings on the outgoing frames of `webSocket`, watching its incoming ones for the answers. */
  def apply(webSocket: WebSocket[F]): WebSocket[F] = webSocket match {
    case WebSocketCombinedPipe(receiveSend, onClose) =>
      WebSocketCombinedPipe[F](
        incoming => withPings(receiveSend(observeAnswers(incoming))),
        onClose,
      )
    case WebSocketSeparatePipe(send, receive, onClose) =>
      WebSocketSeparatePipe[F](withPings(send), receive.compose(observeAnswers), onClose)
  }

  def imapK[G[_]](fk: F ~> G)(gk: G ~> F): Heartbeat[G] =
    new Heartbeat[G](sg => withPings(sg.translate(gk)).translate(fk), isAnswer, fk(answered))

  // Every incoming frame passes through here, so the frames that are not answers must not cost
  // an effect. One answer per chunk settles the heartbeat just as well as all of them.
  private val observeAnswers: Pipe[F, WebSocketFrame, WebSocketFrame] =
    _.chunks.flatMap { chunk =>
      val frames = Stream.chunk(chunk)
      if (chunk.exists(isAnswer)) Stream.exec(answered) ++ frames else frames
    }
}

private[websocket] object Heartbeat {

  /** Sends a `Ping` frame every `every` and closes the connection when the peer does not answer
    * with a matching `Pong` within one interval of the ping being written.
    *
    * @param every  The interval between pings, which doubles as the time the peer has to answer
    *               a ping and as the time the write side has to accept a frame.
    * @param frame  The ping frame to send. A pong is only accepted as an answer to this heartbeat
    *               when it echoes this payload, as required by rfc6455.
    */
  def start[F[_]](every: FiniteDuration, frame: WebSocketFrame.Ping)(implicit
      F: Temporal[F]
  ): F[Heartbeat[F]] =
    (F.ref(false), F.deferred[Unit]).mapN { (pingOutstanding, stalled) =>
      val overdue: WebSocketFrame =
        WebSocketFrame
          .Close(1011, s"No pong received within $every")
          .getOrElse(WebSocketFrame.Close())

      // Pong deadline starts one interval after the write begins (not after handoff from the
      // queue). Stalled writes would stop the clock, so every handoff gets the same deadline.
      // If write doesn't take a frame within one interval, `stalled` interrupts the merged
      // stream without close frame. Interrupt must be on the merged stream, not just this one—
      // a halt inside a blocked merge side doesn't reach the other side.
      val pings: Stream[F, WebSocketFrame] =
        Stream.eval(Queue.synchronous[F, Option[WebSocketFrame]]).flatMap { frames =>
          def handOff(f: Option[WebSocketFrame]): F[Boolean] =
            F.timeoutTo(frames.offer(f).as(true), every, stalled.complete(()).as(false))

          def cycle: F[Unit] =
            pingOutstanding.set(true) *>
              handOff(Some(frame)).flatMap {
                case false => F.unit
                case true =>
                  F.sleep(every) *> pingOutstanding.get.flatMap {
                    case false => cycle
                    case true =>
                      handOff(Some(overdue)).flatMap(sent => F.whenA(sent)(handOff(None).void))
                  }
              }

          Stream
            .repeatEval(frames.take)
            .unNoneTerminate
            .concurrently(Stream.exec(F.sleep(every) *> cycle))
        }

      new Heartbeat[F](
        _.mergeHaltBoth(pings).interruptWhen(stalled.get.attempt),
        // A pong may arrive without a ping ever having been sent, and the send handler is free
        // to ping on its own, so only a pong echoing our payload settles the heartbeat.
        {
          case WebSocketFrame.Pong(data) => data == frame.data
          case _ => false
        },
        pingOutstanding.set(false),
      )
    }
}
