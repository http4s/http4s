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
import org.http4s._
import org.http4s.client.websocket._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.client.internal._
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
import scodec.bits.ByteVector

import scala.concurrent.duration._

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
            case WebSocketFrame.Binary(binary, _) =>
              Stream(WebSocketFrame.Binary(binary))
            case _ =>
              Stream(WebSocketFrame.Text("unknown"))
          }
          wsBuilder.build(sendReceive)
        case GET -> Root / "ws-close" =>
          val send = Stream.eval(F.pure(WebSocketFrame.Text("foo")))
          wsBuilder.build(send, _.void)
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
      s"http://${Uri.Host.fromIp4sHost(address.host).renderString}:${address.port.value}$path"
    )

  def buildWSRequest(url: Uri): WSRequest = WSRequest(
    method = Method.GET,
    uri = url,
    headers = Headers(
      upgradeWebSocket,
      connectionUpgrade,
      `Sec-WebSocket-Version`(supportedWebSocketVersion),
      new `Sec-WebSocket-Key`(ByteVector(Base64.getDecoder().decode(exampleSecWebSocketKey))),
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

  private def fixture =
    (ResourceFunFixture(serverResource), ResourceFunFixture(clientResource), dispatcher).mapN(
      FunFixture.map3(_, _, _)
    )

  fixture.test("open and close connection to server") { case (server, client, _) =>
    val wsRequest = buildWSRequest(url(server.addressIp4s, "/ws-echo"))
    val wsClient = EmberWSClient[IO](client)

    wsClient
      .connect(wsRequest)
      .use(_ => IO.unit)
  }

  fixture.test("send and receive a message") { case (server, client, _) =>
    val wsRequest = buildWSRequest(url(server.addressIp4s, "/ws-echo"))
    val wsClient = EmberWSClient[IO](client)

    wsClient
      .connect(wsRequest)
      .use(conn =>
        for {
          _ <- conn.send(WSFrame.Text("hello"))
          received <- conn.receive
        } yield assertEquals(received, Some(WSFrame.Text("hello"): WSFrame))
      )
  }

  fixture.test("send and receive multiple messages") { case (server, client, _) =>
    val wsRequest = buildWSRequest(url(server.addressIp4s, "/ws-echo"))
    val wsClient = EmberWSClient[IO](client)
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

  fixture.test("open and close high-level connection to server") { case (server, client, _) =>
    val wsRequest = buildWSRequest(url(server.addressIp4s, "/ws-echo"))
    val wsClient = EmberWSClient[IO](client)

    wsClient
      .connectHighLevel(wsRequest)
      .use(_ => IO.unit)
  }

  fixture.test("send and receive a binary frame") { case (server, client, _) =>
    val wsRequest = buildWSRequest(url(server.addressIp4s, "/ws-echo"))
    val wsClient = EmberWSClient[IO](client)
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

  fixture.test("receive a close frame in low-level connection") { case (server, client, _) =>
    val wsRequest = buildWSRequest(url(server.addressIp4s, "/ws-close"))
    val wsClient = EmberWSClient[IO](client)

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

  fixture.test("receive a close frame in high-level connection") { case (server, client, _) =>
    val wsRequest = buildWSRequest(url(server.addressIp4s, "/ws-close"))
    val wsClient = EmberWSClient[IO](client)

    wsClient
      .connectHighLevel(wsRequest)
      .use(conn =>
        for {
          _ <- conn.send(WSFrame.Text("hello"))
          foo <- conn.receive
          _ <- IO.println("fooo",foo)
          // _ <- conn.closeFrame
          // _ <- IO.println("closeFrame",receivedCloseFrame)
        } yield () //assertEquals(receivedCloseFrame, Some(WSFrame.Close(1000, "")))
      )
  }

  fixture.test("automatically close the connection".only) { case (_, client, _) =>
    val closeFrame = WebSocketFrame.Close(1000, "").toTry.get
    val frames = for {
      ref <- Ref[IO].of(List.empty[WebSocketFrame])
      server = EmberServerBuilder
        .default[IO]
        .withPort(port"0")
        .withHttpWebSocketApp { wsb =>
          val dsl = new Http4sDsl[IO] {}
          import dsl._
          HttpRoutes
            .of[IO] { case GET -> Root =>
              wsb
                .withOnClose{
                  println("Adding Close Frame")
                  // ref.update(_ :+ closeFrame)
                  ref.update(_ :+ closeFrame).delayBy(1.second)
                }
                .build(_.evalTap{f => 
                  println("Text frame",f)
                  ref.update(_ :+ f)})
            }
            .orNotFound
        }
        .withShutdownTimeout(1.second)
        .build
        .map(s => buildWSRequest(url(s.addressIp4s, "")))
      _ <- server.use { req =>
        val wsClient = EmberWSClient[IO](client)
        wsClient.connect(req).use(conn => conn.send(WSFrame.Text("hi ember"))) *> 
          IO.sleep(100.millis) *> // quick sleep to collect the close frame
          wsClient.connectHighLevel(req).use { conn =>
            conn.send(WSFrame.Text("hey ember"))
          }
        // IO.println("Disconnect")
      }
      frames <- ref.get
      _ <- IO.println("Ref of frames",frames)
    } yield frames
    frames.assertEquals(
      List(
        WebSocketFrame.Text("hi ember"),
        closeFrame,
        WebSocketFrame.Text("hey ember"),
        closeFrame
      )
    )
  }
}
