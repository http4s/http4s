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

import cats.data.NonEmptyList
import cats.effect._
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Pipe
import fs2.Stream
import org.http4s._
import org.http4s.client.websocket._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Connection
import org.http4s.headers.Upgrade
import org.http4s.implicits._
import org.http4s.server.Server
import org.http4s.server.websocket._
import org.http4s.testing.DispatcherIOFixture
import org.http4s.websocket._
import org.typelevel.ci._
import scodec.bits.ByteVector

class EmberServerWebSocketSuite extends Http4sSuite with DispatcherIOFixture {

  def service[F[_]](wsBuilder: WebSocketBuilder2[F])(implicit F: Async[F]): HttpApp[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes
      .of[F] {
        case GET -> Root =>
          Ok("Hello!")
        case GET -> Root / "ws-echo" =>
          val sendReceive: Pipe[F, WebSocketFrame, WebSocketFrame] = _.flatMap {
            case WebSocketFrame.Text(text, _) => Stream(WebSocketFrame.Text(text))
            case WebSocketFrame.Binary(binary, _) => Stream(WebSocketFrame.Binary(binary))
            case _ => Stream(WebSocketFrame.Text("unknown"))
          }
          wsBuilder.build(sendReceive)
        case GET -> Root / "ws-close" =>
          val send = Stream.eval(F.pure(WebSocketFrame.Text("foo")))
          wsBuilder.build(send, _.void)
        case GET -> Root / "ws-filter-false" =>
          F.deferred[Unit].flatMap { deferred =>
            wsBuilder
              .withFilterPingPongs(false)
              .build(
                Stream.eval(deferred.get).as(WebSocketFrame.Close()),
                _.collect {
                  case WebSocketFrame.Ping(data) if data.decodeAscii.exists(_ == "pingu") =>
                    ()
                }.foreach(deferred.complete(_).void),
              )
          }
      }
      .orNotFound
  }

  val serverResource: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withPort(port"0")
      .withHttpWebSocketApp(service[IO])
      .build

  val clientResource = EmberClientBuilder
    .default[IO]
    .buildWebSocket

  val supportedWebSocketVersion = 13L

  val upgradeCi = ci"upgrade"
  val webSocketProtocol = Protocol(ci"websocket", None)
  val connectionUpgrade = Connection(NonEmptyList.of(upgradeCi))
  val upgradeWebSocket = Upgrade(webSocketProtocol)
  val exampleSecWebSocketKey = "dGhlIHNhbXBsZSBub25jZQ=="

  def url(address: SocketAddress[Host], path: String = ""): Uri =
    Uri.unsafeFromString(
      s"ws://${Uri.Host.fromIp4sHost(address.host).renderString}:${address.port.value}$path"
    )

  def toWSFrame(wsf: WebSocketFrame): WSFrame =
    wsf match {
      case c: WebSocketFrame.Close => WSFrame.Close(c.closeCode, c.reason)
      case WebSocketFrame.Ping(data) => WSFrame.Ping(data)
      case WebSocketFrame.Pong(data) => WSFrame.Pong(data)
      case WebSocketFrame.Text(data, last) => WSFrame.Text(data, last)
      case WebSocketFrame.Binary(data, last) => WSFrame.Binary(data, last)
    }

  private def fixture =
    (ResourceFunFixture(serverResource), ResourceFunFixture(clientResource), dispatcher).mapN(
      FunFixture.map3(_, _, _)
    )

  fixture.test("open and close connection to server") { case (server, (_, wsClient), _) =>
    val wsRequest = WSRequest(url(server.addressIp4s, "/ws-echo"))

    wsClient
      .connect(wsRequest)
      .use(_ => IO.unit)
  }

  fixture.test("send and receive a message") { case (server, (_, wsClient), _) =>
    val wsRequest = WSRequest(url(server.addressIp4s, "/ws-echo"))

    wsClient
      .connect(wsRequest)
      .use(conn =>
        for {
          _ <- conn.send(WSFrame.Text("hello"))
          received <- conn.receive
        } yield assertEquals(received, Some(WSFrame.Text("hello"): WSFrame))
      )
  }

  fixture.test("send and receive multiple messages") { case (server, (_, wsClient), _) =>
    val wsRequest = WSRequest(url(server.addressIp4s, "/ws-echo"))
    val n = 10
    val messages = List.tabulate(n)(i => WSFrame.Text(s"${i + 1}"))
    val expectedMessages = List.tabulate(n)(i => Some(WSFrame.Text(s"${i + 1}")))

    wsClient
      .connect(wsRequest)
      .use(conn =>
        for {
          _ <- conn.sendMany(messages)
          received <- conn.receive.replicateA(n)
        } yield assertEquals(received, expectedMessages)
      )
  }

  fixture.test("send and receive a binary message") { case (server, (_, wsClient), _) =>
    val wsRequest = WSRequest(url(server.addressIp4s, "/ws-echo"))
    val binaryFrame = WSFrame.Binary(ByteVector(100, 100, 100), true)

    wsClient
      .connectHighLevel(wsRequest)
      .use(conn =>
        for {
          _ <- conn.send(binaryFrame)
          received <- conn.receive
        } yield assertEquals(received, Some(binaryFrame))
      )
  }

  fixture.test("respond to pings") { case (server, (_, wsClient), _) =>
    val wsRequest = WSRequest(url(server.addressIp4s, "/ws-echo"))
    val ping = WSFrame.Ping(ByteVector(100, 100, 100))

    wsClient
      .connect(wsRequest)
      .use(conn =>
        for {
          _ <- conn.send(ping)
          received <- conn.receive
        } yield assertEquals(received, Some(WSFrame.Pong(ByteVector(100, 100, 100))))
      )
  }

  fixture.test("initiate close sequence with code=1000 (NORMAL) on stream termination") {
    case (server, (_, wsClient), _) =>
      val wsRequest = WSRequest(url(server.addressIp4s, "/ws-close"))

      wsClient
        .connect(wsRequest)
        .use(conn =>
          for {
            _ <- conn.send(WSFrame.Text("hello"))
            _ <- conn.receive
            receivedCloseFrame <- conn.receive
          } yield assertEquals(receivedCloseFrame, Some(WSFrame.Close(1000, "")))
        )
  }

  fixture.test("respects withFilterPingPongs(false)") { case (server, (_, wsClient), _) =>
    val wsRequest = WSRequest(url(server.addressIp4s, "/ws-filter-false"))
    val ping = WSFrame.Ping(ByteVector("pingu".getBytes()))

    wsClient
      .connect(wsRequest)
      .use(conn =>
        for {
          _ <- conn.send(ping)
        } yield ()
      )
  }
}
