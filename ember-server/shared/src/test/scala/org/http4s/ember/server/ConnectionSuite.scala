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
import com.comcast.ip4s
import com.comcast.ip4s._
import fs2.Chunk
import fs2.Stream
import fs2.io.net._
import org.http4s._
import org.http4s.ember.core.EmberException
import org.http4s.ember.core.Encoder
import org.http4s.ember.core.Parser
import org.http4s.ember.core.Util
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.server.Server

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
          Ok("close").map(_.withHeaders(Connection.close))
        case req @ POST -> Root / "echo" =>
          Ok(req.body)
        case POST -> Root / "unread" =>
          Ok("unread")
      }
      .orNotFound

  val shutdownTimeout = 5.seconds

  def serverResource(
      idleTimeout: FiniteDuration,
      headerTimeout: FiniteDuration,
  ): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withPort(port"0")
      .withHttpApp(service)
      .withIdleTimeout(idleTimeout)
      .withRequestHeaderReceiveTimeout(headerTimeout)
      .withShutdownTimeout(shutdownTimeout)
      .build

  sealed case class TestClient(client: Socket[IO]) {
    private val clientChunkSize = 32 * 1024
    def request(req: Request[IO]): IO[Unit] =
      client.writes(Encoder.reqToBytes(req)).compile.drain
    def response: IO[Response[IO]] =
      Parser.Response
        .parser[IO](Int.MaxValue)(Array.emptyByteArray, client.read(clientChunkSize))
        .map(_._1)
    def responseAndDrain: IO[Unit] =
      response.flatMap(_.body.compile.drain)
    def readChunk: IO[Option[Chunk[Byte]]] =
      client.read(clientChunkSize)
    def writes(bytes: Stream[IO, Byte]): IO[Unit] =
      client.writes(bytes).compile.drain
  }

  def clientResource(host: ip4s.SocketAddress[ip4s.Host]): Resource[IO, TestClient] =
    for {
      socket <- Network[IO].client(host)
    } yield TestClient(socket)

  private def fixture(
      idleTimeout: FiniteDuration = 60.seconds,
      headerTimeout: FiniteDuration = 60.seconds,
  ) = ResourceFunFixture(
    for {
      server <- serverResource(idleTimeout, headerTimeout)
      client <- clientResource(server.addressIp4s)
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
        _ <- client.writes(fs2.text.utf8.encode(request))
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
        _ <- client.writes(fs2.text.utf8.encode(request))
        _ <- client.responseAndDrain
        chunk <- client.readChunk
      } yield assertEquals(chunk, None)
  }

  test("connection close after server socket close") {
    IO.uncancelable { _ =>
      serverResource(30.seconds, 30.seconds).allocated.flatMap { case (server, close) =>
        clientResource(server.addressIp4s).use { c =>
          // Start making request
          c.writes(
            fs2.text.utf8.encode(Stream("GET /keep-alive HTTP/1.1\r\n"))
          ) >>
            // Resolve races to ensure server starts reading request
            IO.sleep(500.millis) >>
            // Close server socket to initiate shutdown
            close.start >> IO.sleep(500.millis) >>
            // Finish making request. Similarly resolve races by giving time for server to start shutdown
            c.writes(
              fs2.text.utf8.encode(Stream("Connection: keep-alive\r\n\r\n"))
            ) >> c.response
              .map { resp =>
                Util.isKeepAlive(HttpVersion.`HTTP/1.1`, resp.headers)
              }
              // Server should set `Connection: close`
              .assertEquals(false)
        }
      }

    }
  }

  test("hard shutdown after timeout") {
    IO.uncancelable { _ =>
      serverResource(30.seconds, 30.seconds).allocated.flatMap { case (server, close) =>
        clientResource(server.addressIp4s).use { c =>
          // Start making request
          c.writes(
            fs2.text.utf8.encode(Stream("GET /keep-alive HTTP/1.1\r\n"))
          ) >>
            // Close server socket to initiate shutdown
            close
        }
      }
    }.timeout(shutdownTimeout + 2.seconds)
  }

}
