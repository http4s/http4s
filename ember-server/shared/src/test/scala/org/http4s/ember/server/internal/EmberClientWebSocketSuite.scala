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
import cats.effect.std.Queue
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import fs2.Pipe
import fs2.Stream
import fs2.io.net.Network
import fs2.io.net.Socket
import org.http4s._
import org.http4s.client.websocket._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.core.WebSocketHelpers.EndOfStreamError
import org.http4s.ember.core.WebSocketHelpers.decodeFrames
import org.http4s.ember.core.WebSocketHelpers.frameToBytes
import org.http4s.ember.core.WebSocketHelpers.serverHandshake
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.headers.Connection
import org.http4s.headers.Upgrade
import org.http4s.headers.`Sec-WebSocket-Protocol`
import org.http4s.server.Server
import org.http4s.server.websocket._
import org.http4s.syntax.all._
import org.http4s.testing.DispatcherIOFixture
import org.http4s.websocket._
import org.typelevel.ci._
import scodec.bits.ByteVector

import java.nio.charset.StandardCharsets

class EmberClientWebSocketSuite extends Http4sSuite with DispatcherIOFixture {

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
        case req @ GET -> Root / "ws-subprotocol-echo" =>
          val responseHeaders =
            req.headers
              .get[`Sec-WebSocket-Protocol`]
              .map(p => Headers(`Sec-WebSocket-Protocol`(p.values.head)))
              .getOrElse(Headers.empty)
          wsBuilder.withHeaders(responseHeaders).build(Stream.empty, _.void)
        case GET -> Root / "ws-subprotocol-wrong" =>
          wsBuilder
            .withHeaders(Headers(`Sec-WebSocket-Protocol`("unoffered")))
            .build(Stream.empty, _.void)
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
    (wsf: @unchecked) match {
      case c: WebSocketFrame.Close => WSFrame.Close(c.closeCode, c.reason)
      case WebSocketFrame.Ping(data) => WSFrame.Ping(data)
      case WebSocketFrame.Pong(data) => WSFrame.Pong(data)
      case WebSocketFrame.Text(data, last) => WSFrame.Text(data, last)
      case WebSocketFrame.Binary(data, last) => WSFrame.Binary(data, last)
    }

  /** A bare-bones WebSocket server speaking directly over TCP, recording every frame it receives,
    * so that tests can observe the exact frames the client puts on the wire.
    */
  def rawWebSocketServer(
      receivedFrames: Queue[IO, WebSocketFrame],
      sendOnOpen: List[WebSocketFrame],
      coalesceHandshake: Boolean = false,
  ): Resource[IO, SocketAddress[IpAddress]] = {
    val address = SocketAddress(ip"127.0.0.1", port"0")
    Network[IO].bind(address).flatMap { serverSocket =>
      serverSocket.accept
        .foreach { socket =>
          for {
            requestHead <- readRequestHead(socket)
            key <- secWebSocketKeyPattern
              .findFirstMatchIn(requestHead)
              .map(_.group(1))
              .liftTo[IO](new RuntimeException("Sec-WebSocket-Key header not found"))
            accept <- serverHandshake[IO](key)
            response =
              "HTTP/1.1 101 Switching Protocols\r\n" +
                "Connection: Upgrade\r\n" +
                "Upgrade: websocket\r\n" +
                s"Sec-WebSocket-Accept: ${accept.toBase64}\r\n" +
                "\r\n"
            responseBytes = Chunk.array(response.getBytes(StandardCharsets.UTF_8))
            openingFrameBytes = sendOnOpen.foldLeft(Chunk.empty[Byte])((acc, frame) =>
              frameToBytes(frame, false).foldLeft(acc)(_ ++ _)
            )
            // When coalescing, write the handshake response and the first frames in a single
            // write so they land in the same read on the client, exercising the path where the
            // HTTP parser reads the start of the WebSocket stream together with the 101 response.
            _ <-
              if (coalesceHandshake) socket.write(responseBytes ++ openingFrameBytes)
              else socket.write(responseBytes) *> socket.write(openingFrameBytes)
            _ <- socket.reads
              .through(decodeFrames[IO](false))
              .foreach(receivedFrames.offer)
              .compile
              .drain
              .recover { case EndOfStreamError() => () }
          } yield ()
        }
        .compile
        .drain
        .background
        .as(serverSocket.address.asIpUnsafe)
    }
  }

  private val secWebSocketKeyPattern = "(?i)Sec-WebSocket-Key:\\s*(\\S+)".r

  private def readRequestHead(socket: Socket[IO], acc: String = ""): IO[String] =
    if (acc.contains("\r\n\r\n")) IO.pure(acc)
    else
      socket.read(4096).flatMap {
        case Some(chunk) =>
          readRequestHead(socket, acc + new String(chunk.toArray, StandardCharsets.UTF_8))
        case None => IO.raiseError(new RuntimeException("Connection closed during handshake"))
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

  fixture.test("automatically close the connection when the client sends a close frame") {
    case (server, (_, wsClient), _) =>
      val wsRequest = WSRequest(url(server.addressIp4s, "/ws-echo"))

      wsClient
        .connect(wsRequest)
        .use(conn =>
          for {
            _ <- conn.send(WSFrame.Text("hello"))
            received <- conn.receive
            _ <- conn.send(WSFrame.Close(1000, ""))
          } yield assertEquals(received, Some(WSFrame.Text("hello"): WSFrame))
        )
  }

  fixture.test("raise when sending after a close frame") { case (server, (_, wsClient), _) =>
    val wsRequest = WSRequest(url(server.addressIp4s, "/ws-echo"))

    wsClient
      .connect(wsRequest)
      .use(conn =>
        for {
          _ <- conn.send(WSFrame.Close(1000, ""))
          attempt <- conn.send(WSFrame.Text("after close")).attempt
        } yield assertEquals(
          attempt.left.map(_.getMessage),
          Left("Connection already closed"),
        )
      )
  }

  test("send transmits the close code and reason supplied by the user") {
    val resources = for {
      receivedFrames <- Resource.eval(Queue.unbounded[IO, WebSocketFrame])
      address <- rawWebSocketServer(receivedFrames, sendOnOpen = Nil)
      clientAndWsClient <- EmberClientBuilder.default[IO].buildWebSocket
    } yield (receivedFrames, address, clientAndWsClient._2)

    resources.use { case (receivedFrames, address, wsClient) =>
      for {
        _ <- wsClient
          .connect(WSRequest(url(address)))
          .use(_.send(WSFrame.Close(4000, "test reason")))
        expected <- IO.fromEither(WebSocketFrame.Close(4000, "test reason"))
        received <- receivedFrames.take
      } yield assertEquals(received, expected: WebSocketFrame)
    }
  }

  test("connection release echoes the close code received from the server") {
    val resources = for {
      receivedFrames <- Resource.eval(Queue.unbounded[IO, WebSocketFrame])
      serverClose <- Resource.eval(IO.fromEither(WebSocketFrame.Close(4001, "going away")))
      address <- rawWebSocketServer(receivedFrames, sendOnOpen = List(serverClose))
      clientAndWsClient <- EmberClientBuilder.default[IO].buildWebSocket
    } yield (receivedFrames, serverClose, address, clientAndWsClient._2)

    resources.use { case (receivedFrames, serverClose, address, wsClient) =>
      for {
        received <- wsClient.connect(WSRequest(url(address))).use(_.receive)
        echoed <- receivedFrames.take
      } yield {
        assertEquals(received, Some(WSFrame.Close(4001, "going away"): WSFrame))
        assertEquals(echoed, serverClose: WebSocketFrame)
      }
    }
  }

  test("replay a frame coalesced with the 101 handshake response") {
    val firstFrame = WebSocketFrame.Text("coalesced")
    val resources = for {
      receivedFrames <- Resource.eval(Queue.unbounded[IO, WebSocketFrame])
      address <- rawWebSocketServer(
        receivedFrames,
        sendOnOpen = List(firstFrame),
        coalesceHandshake = true,
      )
      clientAndWsClient <- EmberClientBuilder.default[IO].buildWebSocket
    } yield (address, clientAndWsClient._2)

    resources.use { case (address, wsClient) =>
      wsClient
        .connect(WSRequest(url(address)))
        .use(_.receive)
        .map(received => assertEquals(received, Some(WSFrame.Text("coalesced"): WSFrame)))
    }
  }

  fixture.test("open and close high-level connection to server") {
    case (server, (_, wsClient), _) =>
      val wsRequest = WSRequest(url(server.addressIp4s, "/ws-echo"))

      wsClient
        .connectHighLevel(wsRequest)
        .use(_ => IO.unit)
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

  fixture.test("receive a close frame in low-level connection") { case (server, (_, wsClient), _) =>
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

  test("reassemble a fragmented text message received across continuation frames") {
    val fragments = List(
      WebSocketFrame.Text("Hello ", last = false),
      WebSocketFrame.Continuation(
        ByteVector("world".getBytes(StandardCharsets.UTF_8)),
        last = true,
      ),
    )
    val resources = for {
      receivedFrames <- Resource.eval(Queue.unbounded[IO, WebSocketFrame])
      address <- rawWebSocketServer(receivedFrames, sendOnOpen = fragments)
      clientAndWsClient <- EmberClientBuilder.default[IO].buildWebSocket
    } yield (address, clientAndWsClient._2)

    resources.use { case (address, wsClient) =>
      wsClient
        .connect(WSRequest(url(address)))
        .use(_.receive)
        .map(received => assertEquals(received, Some(WSFrame.Text("Hello world"))))
    }
  }

  test("high-level connection reassembles a fragmented text message") {
    val fragments = List(
      WebSocketFrame.Text("Hello ", last = false),
      WebSocketFrame.Continuation(
        ByteVector("world".getBytes(StandardCharsets.UTF_8)),
        last = true,
      ),
    )
    val resources = for {
      receivedFrames <- Resource.eval(Queue.unbounded[IO, WebSocketFrame])
      address <- rawWebSocketServer(receivedFrames, sendOnOpen = fragments)
      clientAndWsClient <- EmberClientBuilder.default[IO].buildWebSocket
    } yield (address, clientAndWsClient._2)

    resources.use { case (address, wsClient) =>
      wsClient
        .connectHighLevel(WSRequest(url(address)))
        .use(_.receive)
        .map(received => assertEquals(received, Some(WSFrame.Text("Hello world"))))
    }
  }

  fixture.test("low-level receiveStream terminates after the server closes") {
    case (server, (_, wsClient), _) =>
      val wsRequest = WSRequest(url(server.addressIp4s, "/ws-close"))

      wsClient
        .connect(wsRequest)
        .use(_.receiveStream.compile.toList)
        .map(frames =>
          assertEquals(
            frames,
            List(WSFrame.Text("foo"): WSFrame, WSFrame.Close(1000, "")),
          )
        )
  }

  fixture.test("high-level receiveStream terminates after the server closes") {
    case (server, (_, wsClient), _) =>
      val wsRequest = WSRequest(url(server.addressIp4s, "/ws-close"))

      wsClient
        .connectHighLevel(wsRequest)
        .use(_.receiveStream.compile.toList)
        .map(frames => assertEquals(frames, List(WSFrame.Text("foo"))))
  }

  fixture.test("propagate ServerHandshakeError when the server does not upgrade") {
    case (server, (_, wsClient), _) =>
      val wsRequest = WSRequest(url(server.addressIp4s, "/"))

      wsClient
        .connect(wsRequest)
        .use(_ => IO.unit)
        .attempt
        .map(attempt =>
          assertEquals(
            attempt.left.map(_.getMessage),
            Left("Not found HTTP Status 101 Switching Protocol."),
          )
        )
  }

  fixture.test("subprotocol surfaces the server-selected value") {
    case (server, (_, wsClient), _) =>
      val wsRequest = WSRequest(url(server.addressIp4s, "/ws-subprotocol-echo"))
        .withHeaders(Headers(`Sec-WebSocket-Protocol`("v4.channel.k8s.io")))

      wsClient
        .connect(wsRequest)
        .use(conn => IO(assertEquals(conn.subprotocol, Some("v4.channel.k8s.io"))))
  }

  fixture.test("subprotocol is None when no subprotocol is negotiated") {
    case (server, (_, wsClient), _) =>
      val wsRequest = WSRequest(url(server.addressIp4s, "/ws-echo"))

      wsClient
        .connect(wsRequest)
        .use(conn => IO(assertEquals(conn.subprotocol, None)))
  }

  fixture.test("fail the connection when the server selects a subprotocol that was not offered") {
    case (server, (_, wsClient), _) =>
      val wsRequest = WSRequest(url(server.addressIp4s, "/ws-subprotocol-wrong"))
        .withHeaders(Headers(`Sec-WebSocket-Protocol`("v4.channel.k8s.io")))

      wsClient
        .connect(wsRequest)
        .use(_ => IO.unit)
        .attempt
        .map(attempt =>
          assertEquals(
            attempt.left.map(_.getMessage),
            Left("Sec-WebSocket-Protocol does not match a subprotocol offered by the client"),
          )
        )
  }

}
