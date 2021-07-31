/*
 * Copyright 2019 http4s.org
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

package org.http4s.ember.server

import cats.effect._
import cats.effect.std.Dispatcher
import cats.effect.std.Queue
import org.http4s.server.Server
import org.java_websocket.WebSocket
import org.java_websocket.client.WebSocketClient
import org.java_websocket.framing.Framedata
import org.java_websocket.framing.PingFrame
import org.java_websocket.handshake.ServerHandshake

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

trait EmberServerWebSocketSuitePlatform { self: EmberServerWebSocketSuite =>

  def serverURI(server: Server, path: String): URI =
    URI.create(s"ws://${server.address}/$path")

  case class Client(
      waitOpen: Deferred[IO, Option[Throwable]],
      waitClose: Deferred[IO, Option[Throwable]],
      messages: Queue[IO, String],
      pongs: Queue[IO, String],
      remoteClosed: Deferred[IO, Unit],
      client: WebSocketClient) {
    def connect: IO[Unit] =
      IO(client.connect()) >> waitOpen.get.flatMap(ex => IO.fromEither(ex.toLeft(())))
    def close: IO[Unit] =
      IO(client.close()) >> waitClose.get.flatMap(ex => IO.fromEither(ex.toLeft(())))
    def send(msg: String): IO[Unit] = IO(client.send(msg))
    def ping(data: String): IO[Unit] = IO {
      val frame = new PingFrame()
      frame.setPayload(ByteBuffer.wrap(data.getBytes(StandardCharsets.UTF_8)))
      client.sendFrame(frame)
    }
  }

  def createClient(target: URI, dispatcher: Dispatcher[IO]): IO[Client] =
    for {
      waitOpen <- Deferred[IO, Option[Throwable]]
      waitClose <- Deferred[IO, Option[Throwable]]
      queue <- Queue.unbounded[IO, String]
      pongQueue <- Queue.unbounded[IO, String]
      remoteClosed <- Deferred[IO, Unit]
      client = new WebSocketClient(target) {
        override def onOpen(handshakedata: ServerHandshake): Unit = {
          val fa = waitOpen.complete(None)
          dispatcher.unsafeRunSync(fa)
          ()
        }
        override def onClose(code: Int, reason: String, remote: Boolean): Unit = {
          val fa = waitOpen
            .complete(Some(new Throwable(s"closed: code: $code, reason: $reason")))
            .attempt >> waitClose.complete(None)
          dispatcher.unsafeRunSync(fa)
          ()
        }
        override def onMessage(msg: String): Unit =
          dispatcher.unsafeRunSync(queue.offer(msg))
        override def onError(ex: Exception): Unit = {
          val fa = waitOpen.complete(Some(ex)).attempt >> waitClose.complete(Some(ex)).attempt.void
          dispatcher.unsafeRunSync(fa)
        }
        override def onWebsocketPong(conn: WebSocket, f: Framedata): Unit = {
          val fa = pongQueue
            .offer(new String(f.getPayloadData().array(), StandardCharsets.UTF_8))
          dispatcher.unsafeRunSync(fa)
        }
        override def onClosing(code: Int, reason: String, remote: Boolean): Unit = {
          dispatcher.unsafeRunSync(remoteClosed.complete(()))
          ()
        }
      }
    } yield Client(waitOpen, waitClose, queue, pongQueue, remoteClosed, client)

}
