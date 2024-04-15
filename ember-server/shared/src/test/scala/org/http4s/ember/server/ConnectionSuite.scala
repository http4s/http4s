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
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.server.Server

import scala.concurrent.duration._

class ConnectionSuite extends Http4sSuite {

  import org.http4s.dsl.io._
  import org.http4s.client.dsl.io._

  val defaultIdleTimeout: FiniteDuration = 60.seconds
  def defaultHeaderTimeout: FiniteDuration = defaultIdleTimeout

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

  def emberServerBuilder(
      idleTimeout: FiniteDuration,
      headerTimeout: FiniteDuration,
  ): EmberServerBuilder[IO] =
    EmberServerBuilder
      .default[IO]
      .withPort(port"0")
      .withHttpApp(service)
      .withIdleTimeout(idleTimeout)
      .withRequestHeaderReceiveTimeout(headerTimeout)

  def serverResource(
      idleTimeout: FiniteDuration,
      headerTimeout: FiniteDuration,
  ): Resource[IO, Server] =
    emberServerBuilder(idleTimeout, headerTimeout).build

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

    /** Like [[#response]], but drains the body and just returns the prelude. */
    def responsePrelude: IO[ResponsePrelude] =
      response.flatMap(response =>
        response.body.compile.drain.as(ResponsePrelude.fromResponse(response))
      )
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
      idleTimeout: FiniteDuration = defaultIdleTimeout,
      headerTimeout: FiniteDuration = defaultHeaderTimeout,
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

  fixture().test("#6931 - bad request on request/start-line which can not be parsed") { client =>
    val request = Stream(
      "GET /hello/colt?foo={ HTTP/1.1\r\n\r\n"
    )

    for {
      _ <- client.writes(fs2.text.utf8.encode(request))
      resp <- client.responsePrelude
    } yield assertEquals(resp.status, Status.BadRequest)
  }

  ResourceFunFixture(
    emberServerBuilder(defaultIdleTimeout, defaultHeaderTimeout)
      .withRequestLineParseErrorHandler { case _ =>
        IO.pure(Response(Status.InternalServerError))
      }
      .build
      .flatMap(server => clientResource(server.addressIp4s))
  ).test(
    "#6931 - respect user configured behavior for request/start-line which can not be parsed"
  ) { client =>
    val request = Stream(
      "GET /hello/colt?foo={ HTTP/1.1\r\n\r\n"
    )

    for {
      _ <- client.writes(fs2.text.utf8.encode(request))
      resp <- client.responsePrelude
    } yield assertEquals(resp.status, Status.InternalServerError)
  }

  ResourceFunFixture(
    emberServerBuilder(defaultIdleTimeout, defaultHeaderTimeout)
      .withMaxHeaderSize(100)
      .build
      .flatMap(server => clientResource(server.addressIp4s))
  ).test(
    "return 431 by default on excessive header size"
  ) { client =>
    val tooMuchHeader = "X-Trash: " + ("." * 120)
    val request = Stream(s"GET / HTTP/1.0\r\n${tooMuchHeader}\r\n")
    for {
      _ <- client.writes(fs2.text.utf8.encode(request))
      resp <- client.responsePrelude
    } yield assertEquals(resp.status.code, 431)
  }

  ResourceFunFixture(
    emberServerBuilder(defaultIdleTimeout, defaultHeaderTimeout)
      .withMaxHeaderSize(100)
      .withMaxHeaderSizeErrorHandler { case _ =>
        IO.fromEither(Status.fromInt(499)).map(Response(_))
      }
      .build
      .flatMap(server => clientResource(server.addressIp4s))
  ).test(
    "respect user configured behavior for excessive header size "
  ) { client =>
    val tooMuchHeader = "X-Trash: " + ("." * 120)
    val request = Stream(s"GET / HTTP/1.0\r\n${tooMuchHeader}\r\n")
    for {
      _ <- client.writes(fs2.text.utf8.encode(request))
      resp <- client.responsePrelude
    } yield assertEquals(resp.status.code, 499)
  }

}
