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
package client
package testkit

import cats.effect._
import cats.effect.std.Queue
import cats.implicits.toBifunctorOps
import fs2.Stream
import org.http4s.client.websocket.WSClient
import org.http4s.client.websocket.WSFrame
import org.http4s.client.websocket.WSRequest
import org.http4s.dsl.io._
import org.http4s.server.websocket.WebSocketBuilder2
import org.http4s.syntax.all._
import org.http4s.websocket.WebSocketFrame

class WSTestClientSuite extends Http4sSuite {
  import WSTestClient._

  private def separatePipeWsApp(
      send: Queue[IO, WebSocketFrame],
      receive: Queue[IO, WebSocketFrame],
  ): IO[WSClient[IO]] =
    fromHttpWebSocketApp[IO] { (wsb: WebSocketBuilder2[IO]) =>
      HttpRoutes
        .of[IO] { case GET -> Root / "ws" =>
          wsb
            .withHeaders(Headers("Sec-WebSocket-Protocol" -> "data"))
            .build(Stream.fromQueueUnterminated(send), _.evalMap(receive.offer))
        }
        .orNotFound
    }

  private def combinedPipeWsApp: IO[WSClient[IO]] = fromHttpWebSocketApp[IO] {
    (wsb: WebSocketBuilder2[IO]) =>
      HttpRoutes
        .of[IO] { case GET -> Root / "ws" =>
          wsb
            .withHeaders(Headers("Sec-WebSocket-Protocol" -> "data"))
            .build(identity)
        }
        .orNotFound
  }

  test("separate pipes should handle all messages") {
    val msgFromServer = "Hello"
    val msgFromClient = "World"
    for {
      sendQueue <- Queue.unbounded[IO, WebSocketFrame]
      receiveQueue <- Queue.unbounded[IO, WebSocketFrame]
      client <- separatePipeWsApp(sendQueue, receiveQueue)
      actualMsgFromServer <- client.connect(WSRequest(uri"/ws")).use { connection =>
        assertEquals(connection.subprotocol, Some("data"))
        for {
          _ <- sendQueue.offer(WebSocketFrame.Text(msgFromServer))
          r <- connection.receive
          _ <- connection.send(WSFrame.Text(msgFromClient))
          _ <- connection.send(WSFrame.Close(1000, "OK"))
        } yield r
      }
      receiveChannelMsgs <- receiveQueue.tryTakeN(None)
    } yield {
      assertEquals(actualMsgFromServer, Some(WSFrame.Text(msgFromServer)))
      assertEquals(
        receiveChannelMsgs.map(_.toWSFrame),
        List(WSFrame.Text(msgFromClient), WSFrame.Close(1000, "OK")),
      )
    }
  }

  test("combined pipe should handle all messages") {
    val expectedMsg = "hello"
    Resource
      .liftK(combinedPipeWsApp)
      .flatMap(_.connect(WSRequest(uri"/ws")))
      .use { connection =>
        assertEquals(connection.subprotocol, Some("data"))
        for {
          _ <- connection.send(WSFrame.Text(expectedMsg))
          _ <- connection.send(WSFrame.Text(expectedMsg))
          _ <- connection.send(WSFrame.Close(1000, "OK"))
          results <- connection.receiveStream.compile.toList
        } yield results
      }
      .assertEquals(
        List(WSFrame.Text(expectedMsg), WSFrame.Text(expectedMsg), WSFrame.Close(1000, "OK"))
      )
  }

  test("if app doesn't respond with a websocket, the connection initialization should fail") {

    Resource
      .liftK(
        fromHttpWebSocketApp[IO]((_: WebSocketBuilder2[IO]) =>
          HttpRoutes
            .of[IO] { case GET -> Root / "ws" =>
              Ok("Hello")
            }
            .orNotFound
        )
      )
      .flatMap(_.connect(WSRequest(uri"/ws")))
      .use(_ => IO.unit)
      .attempt
      .map(_.leftMap(_.getClass))
      .assertEquals(Left(classOf[WebSocketClientInitException]))
  }

}
