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

import _root_.fs2.Chunk
import cats.effect._
import fs2.Stream
import fs2.io.tcp.Socket
import fs2.io.tcp.SocketGroup
import org.http4s._
import org.http4s.ember.core.EmberException
import org.http4s.ember.core.Encoder
import org.http4s.ember.core.Parser
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.server.Server
import org.typelevel.ci._

import java.net.InetSocketAddress
import scala.concurrent.duration._

class ConnectionSuite extends Http4sSuite {

  import org.http4s.dsl.io._
  import org.http4s.client.dsl.io._

  def service: HttpApp[IO] =
    HttpRoutes
      .of[IO] {
        case GET -> Root =>
          Ok("ok")
        case GET -> Root / "keep-alive" =>
          Ok("keep-alive") // keep-alive enabled by default
        case GET -> Root / "close" =>
          Ok("close").map(_.withHeaders(Connection(ci"close")))
        case req @ POST -> Root / "echo" =>
          Ok(req.body)
        case POST -> Root / "unread" =>
          Ok("unread")
      }
      .orNotFound

  def serverResource(
      idleTimeout: FiniteDuration,
      headerTimeout: FiniteDuration,
  ): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHttpApp(service)
      .withIdleTimeout(idleTimeout)
      .withRequestHeaderReceiveTimeout(headerTimeout)
      .build

  sealed case class TestClient(client: Socket[IO]) {
    val clientChunkSize = 32 * 1024
    def request(req: Request[IO]): IO[Unit] =
      client.writes(None)(Encoder.reqToBytes(req)).compile.drain
    def response: IO[Response[IO]] =
      Parser.Response
        .parser[IO](Int.MaxValue)(Array.emptyByteArray, client.read(clientChunkSize, None))
        .map(_._1)
    def responseAndDrain: IO[Unit] =
      response.flatMap(_.body.compile.drain)
    def readChunk: IO[Option[Chunk[Byte]]] =
      client.read(clientChunkSize, None)
    def writes(bytes: Stream[IO, Byte]): IO[Unit] =
      client.writes(None)(bytes).compile.drain
  }

  def clientResource(host: InetSocketAddress): Resource[IO, TestClient] =
    for {
      blocker <- Blocker[IO]
      socketGroup <- SocketGroup[IO](blocker)
      socket <- socketGroup.client[IO](host)
    } yield TestClient(socket)

  def fixture(
      idleTimeout: FiniteDuration = 60.seconds,
      headerTimeout: FiniteDuration = 60.seconds,
  ) = ResourceFixture(
    for {
      server <- serverResource(idleTimeout, headerTimeout)
      client <- clientResource(server.address)
    } yield client
  )

  fixture().test("close connection") { client =>
    val request = GET(uri"http://localhost:9000/close")
    for {
      _ <- client.request(request)
      _ <- client.responseAndDrain
      chunk <- client.readChunk
    } yield assertEquals(chunk, None)
  }

  fixture().test("keep-alive connection") { client =>
    val req1 = GET(uri"http://localhost:9000/keep-alive")
    val req2 = GET(uri"http://localhost:9000/close")
    for {
      _ <- client.request(req1)
      _ <- client.responseAndDrain
      _ <- client.request(req2)
      _ <- client.responseAndDrain
      chunk <- client.readChunk
    } yield assertEquals(chunk, None)
  }

  fixture(idleTimeout = 1.seconds)
    .test("read timeout during header terminates connection with no response") { client =>
      val request = GET(uri"http://localhost:9000/close")
      for {
        _ <- client.writes(Encoder.reqToBytes(request).take(10))
        chunk <- client.readChunk
      } yield assertEquals(chunk, None)
    }

  fixture(idleTimeout = 1.seconds).test("read timeout during body terminates connection") {
    client =>
      val request = Stream(
        "POST /echo HTTP/1.1\r\n",
        "Accept: text/plain\r\n",
        "Content-Length: 100\r\n\r\n",
        "less than 100 bytes",
      )
      (for {
        _ <- client.writes(fs2.text.utf8Encode(request))
        _ <- client.responseAndDrain
        _ <- client.readChunk
      } yield ()).intercept[EmberException.ReachedEndOfStream]
  }

  fixture(headerTimeout = 1.seconds).test("header timeout terminates connection with no response") {
    client =>
      val request = GET(uri"http://localhost:9000/close")
      for {
        _ <- client.writes(Encoder.reqToBytes(request).take(10))
        chunk <- client.readChunk
      } yield assertEquals(chunk, None)
  }

  fixture().test("close connection after response when request body stream is partially read") {
    client =>
      val request = Stream(
        "POST /unread HTTP/1.1\r\n",
        "Accept: text/plain\r\n",
        "Content-Length: 100\r\n\r\n",
        "not enough bytes",
      )
      for {
        _ <- client.writes(fs2.text.utf8Encode(request))
        _ <- client.responseAndDrain
        chunk <- client.readChunk
      } yield assertEquals(chunk, None)
  }

}
