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

package org.http4s.ember.server.internal

import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Pipe
import fs2.Stream
import fs2.io.net.Socket
import org.http4s._
import org.http4s.client.websocket.WSFrame
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.core.WebSocketHelpers.frameToBytes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Connection
import org.http4s.headers.Upgrade
import org.http4s.headers.`Sec-WebSocket-Key`
import org.http4s.headers.`Sec-WebSocket-Version`
import org.http4s.server.Server
import org.http4s.server.websocket._
import org.http4s.testing.DispatcherIOFixture
import org.http4s.websocket._
import org.typelevel.ci._
import org.typelevel.vault._
import scodec.bits.ByteVector

import java.util.Base64

class ExampleWebSocketClientSuite extends Http4sSuite with DispatcherIOFixture {

  def service[F[_]](wsBuilder: WebSocketBuilder2[F])(implicit F: Async[F]): HttpApp[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes
      .of[F] {
        case GET -> Root =>
          Ok("Hello")
        case GET -> Root / "ws-echo" =>
          val sendReceive: Pipe[F, WebSocketFrame, WebSocketFrame] = _.flatMap {
            case WebSocketFrame.Text(text, _) =>
              Stream(WebSocketFrame.Text(text))
            case _ =>
              Stream(WebSocketFrame.Text("unknown"))
          }
          wsBuilder.build(sendReceive)
      }
      .orNotFound
  }

  val serverResource: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withPort(port"0")
      .withHttpWebSocketApp(service[IO])
      .build

  val socketKey: Key[Socket[IO]] = Key.newKey[SyncIO, Socket[IO]].unsafeRunSync()

  val emberClient = EmberClientBuilder
    .default[IO]
    .buildWebSocket(socketKey)

  val supportedWebSocketVersion = 13L

  val upgradeCi = ci"upgrade"
  val webSocketProtocol = Protocol(ci"websocket", None)
  val connectionUpgrade = Connection(NonEmptyList.of(upgradeCi))
  val upgradeWebSocket = Upgrade(webSocketProtocol)
  val secWebSocketKey = "dGhlIHNhbXBsZSBub25jZQ=="

  def wsRequest(url: String): Request[IO] = Request[IO](
    method = Method.GET,
    uri = Uri.unsafeFromString(url),
    headers = Headers(
      upgradeWebSocket,
      connectionUpgrade,
      `Sec-WebSocket-Version`(supportedWebSocketVersion),
      new `Sec-WebSocket-Key`(ByteVector(Base64.getDecoder().decode(secWebSocketKey))),
    ),
  )

  def toWSFrame(wsf: WebSocketFrame): WSFrame =
    wsf match {
      case c: WebSocketFrame.Close => WSFrame.Close(c.closeCode, c.reason)
      case WebSocketFrame.Ping(data) => WSFrame.Ping(data)
      case WebSocketFrame.Pong(data) => WSFrame.Pong(data)
      case WebSocketFrame.Text(data, last) => WSFrame.Text(data, last)
      case WebSocketFrame.Binary(data, last) => WSFrame.Binary(data, last)
    }

  private def fixture = (ResourceFunFixture(serverResource), dispatcher).mapN(FunFixture.map2(_, _))

  fixture.test("Ember WebSocket Client") { case (server, _) =>
    for {
      _ <- emberClient
        .use { client =>
          client
            .run(wsRequest(s"ws://${server.addressIp4s.host}:${server.addressIp4s.port}/ws-echo"))
            .use { res =>
              val socket = res.attributes.lookup(socketKey).get
              for {
                _ <- frameToBytes(WebSocketFrame.Text("hello"), true)
                  .traverse_(c => socket.write(c))
                received <- socket.reads.take(7).evalTap(b => IO.println(b.toChar)).compile.drain
              } yield received
            }
        }
    } yield ()
  }
}
