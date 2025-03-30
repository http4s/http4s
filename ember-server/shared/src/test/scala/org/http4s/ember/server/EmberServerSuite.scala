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
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Stream
import fs2.io.net.BindException
import fs2.io.net.ConnectException
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.core.EmberException
import org.http4s.ember.server.EmberServerBuilder.Defaults
import org.http4s.h2.H2Keys.Http2PriorKnowledge
import org.http4s.implicits._
import org.http4s.server.Server

import scala.concurrent.duration._

class EmberServerSuite extends Http4sSuite {

  def service: HttpApp[IO] = {
    import org.http4s.dsl.io._

    HttpRoutes
      .of[IO] {
        case GET -> Root =>
          Ok("Hello!")
        case req @ POST -> Root / "echo" =>
          Ok(req.body)
        case GET -> Root / "failed-stream" =>
          Ok(Stream.raiseError[IO](new RuntimeException("BOOM")).covaryOutput[String])
        case POST -> Root / "ignored-body" / foo =>
          Ok(s"$foo")
      }
      .orNotFound
  }

  def url(address: SocketAddress[Host], path: String = ""): Uri =
    Uri.unsafeFromString(
      s"http://${Uri.Host.fromIp4sHost(address.host).renderString}:${address.port.value}$path"
    )

  def serverResource(
      f: EmberServerBuilder[IO] => EmberServerBuilder[IO] = identity
  ): Resource[IO, Server] =
    f(
      EmberServerBuilder
        .default[IO]
        .withPort(port"0")
        .withHttpApp(service)
    ).build

  private val client = ResourceFunFixture(EmberClientBuilder.default[IO].build)

  private def server(receiveBufferSize: Int = Defaults.receiveBufferSize) = ResourceFunFixture(
    EmberServerBuilder
      .default[IO]
      .withHttpApp(service)
      .withPort(port"0")
      .withReceiveBufferSize(receiveBufferSize)
      .build
  )

  private def fixture(receiveBufferSize: Int = Defaults.receiveBufferSize) =
    (server(receiveBufferSize), client).mapN(FunFixture.map2(_, _))

  fixture().test("server responds to requests") { case (server, client) =>
    client
      .get(url(server.addressIp4s))(_.status.pure[IO])
      .assertEquals(Status.Ok)
  }

  fixture().test("connection closed when response body stream fails") { case (server, client) =>
    val req = Request[IO](uri = url(server.addressIp4s, "/failed-stream"))
    client
      .stream(req)
      .flatMap(_.body)
      .compile
      .drain
      .timeout(1.second)
      .intercept[EmberException.ReachedEndOfStream] >>
      // server should still be alive
      client
        .get(url(server.addressIp4s))(_.status.pure[IO])
        .assertEquals(Status.Ok)
  }

  client.test("server shuts down after exiting resource scope") { client =>
    serverResource().use(server => IO.pure(server.addressIp4s)).flatMap { address =>
      client
        .get(url(address))(_.status.pure[IO])
        .intercept[ConnectException]
    }
  }

  client.test("shutdown timeout of 0 doesn't reset connections") { client =>
    serverResource(_.withShutdownTimeout(0.nanos)).use { server =>
      client.expect[String](url(server.addressIp4s)).assertEquals("Hello!")
    }
  }

  server().test("server startup fails if address is already in use") { server =>
    EmberServerBuilder
      .default[IO]
      .withPort(server.addressIp4s.port)
      .build
      .use(_ => IO.unit)
      .intercept[BindException]
  }

  fixture(receiveBufferSize = 256).test("#4731 - read socket is drained after writing") {
    case (server, client) =>
      // We set the receive buffer size to be smaller than the request body,
      // so that draining is necessary for a second request.
      import org.http4s.dsl.io._
      import org.http4s.client.dsl.io._

      val body: Stream[IO, Byte] =
        Stream.emit("hello").repeatN(256).through(fs2.text.utf8.encode)
      val expected = "hello" * 256

      val uri = url(server.addressIp4s, "/echo")
      val request = POST(body, uri)
      for {
        r1 <- client.fetchAs[String](request)
        r2 <- client.fetchAs[String](request)
      } yield assertEquals(expected, r1) && assertEquals(expected, r2)
  }

  client.test("#4935 - client can detect a terminated connection") { client =>
    def runReq(server: Server) = {
      val req =
        Request[IO](Method.POST, uri = url(server.addressIp4s, "/echo")).withEntity("Hello!")
      client.expect[String](req).assertEquals("Hello!")
    }

    serverResource(_.withShutdownTimeout(0.nanos))
      .use(server => runReq(server).as(server.addressIp4s.port))
      .flatMap { port =>
        IO.sleep(1.second) *> // so server shutdown propagates
          serverResource(_.withPort(port).withShutdownTimeout(0.nanos)).use(runReq(_))
      }
  }

  test("#7146 - HTTP/2 request with body") {
    val server = EmberServerBuilder
      .default[IO]
      .withPort(port"0")
      .withHttp2
      .withHttpApp(service)
      .build

    val client = EmberClientBuilder
      .default[IO]
      .withHttp2
      .build

    (server, client).tupled.use { case (server, client) =>
      val req = Request[IO](Method.POST, uri = url(server.addressIp4s, "/echo"))
        .withEntity("hello")
        .withAttribute(Http2PriorKnowledge, ())
      client.expect[String](req).assertEquals("hello")
    }
  }

  test("#7216 - client can replace a terminated connection with max total of 1") {
    EmberClientBuilder.default[IO].withMaxTotal(1).build.use { client =>
      def runReq(server: Server) = {
        val req =
          Request[IO](Method.POST, uri = url(server.addressIp4s, "/echo")).withEntity("Hello!")
        client.expect[String](req).assertEquals("Hello!")
      }

      serverResource(_.withShutdownTimeout(0.nanos))
        .use(server => runReq(server).as(server.addressIp4s.port))
        .flatMap { port =>
          IO.sleep(1.second) *> // so server shutdown propagates
            serverResource(_.withPort(port).withShutdownTimeout(0.nanos)).use(runReq(_))
        }
    }
  }

  fixture().test(
    "#7655 — client shouldn't fail request processing when server ignores the request body"
  ) { case (server, client) =>
    def runReq[A: EntityEncoder[IO, *]](server: Server, entity: A) = {
      val req =
        Request[IO](Method.POST, uri = url(server.addressIp4s, "/ignored-body/what"))
          .withEntity(entity)
      client
        .expect[String](req)
        .timeout(1.second)
        .assertEquals("what")
    }

    runReq(server, Array.fill(1024 * 1024)(42.toByte))
  }
}
