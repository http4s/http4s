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

import cats.syntax.all._
import cats.effect._
import com.comcast.ip4s.SocketAddress
import com.comcast.ip4s.Host
import fs2.Stream
import org.http4s._
import org.http4s.server.Server
import org.http4s.implicits._
import org.http4s.dsl.Http4sDsl
import org.http4s.ember.client.EmberClientBuilder

class EmberServerSuite extends Http4sSuite with EmberServerSuitePlatform {

  def service[F[_]](implicit F: Async[F]): HttpApp[F] = {
    val dsl = new Http4sDsl[F] {}
    import dsl._

    HttpRoutes
      .of[F] {
        case GET -> Root =>
          Ok("Hello!")
        case req @ POST -> Root / "echo" =>
          Ok(req.body)
      }
      .orNotFound
  }

  def url(address: SocketAddress[Host], path: String = ""): String =
    s"http://${address.host}:${address.port.value}$path"

  val serverResource: Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHttpApp(service[IO])
      .build

  val client = ResourceFixture(EmberClientBuilder.default[IO].build)

  def server(receiveBufferSize: Int = 256 * 1024) = ResourceFixture(
    EmberServerBuilder
      .default[IO]
      .withHttpApp(service[IO])
      .withReceiveBufferSize(receiveBufferSize)
      .build)

  def fixture(receiveBufferSize: Int = 256 * 1024) =
    (server(receiveBufferSize), client).mapN(FunFixture.map2(_, _))

  fixture().test("server responds to requests") { case (server, client) =>
    client
      .get(url(server.address))(_.status.pure[IO])
      .assertEquals(Status.Ok)
  }

  client.test("server shuts down after exiting resource scope") { client =>
    serverResource.use(server => IO.pure(server.address)).flatMap { address =>
      client
        .get(url(address))(_.status.pure[IO])
        .intercept[ConnectException]
    }
  }

  server().test("server startup fails if address is already in use") { case _ =>
    serverResource.use(_ => IO.unit).intercept[BindException]
  }

  fixture(receiveBufferSize = 256).test("#4731 - read socket is drained after writing") {
    case (server, client) =>
      // We set the receive buffer size to be smaller than the request body,
      // so that draining is necessary for a second request.
      import org.http4s.dsl.io._
      import org.http4s.client.dsl.io._

      val body: Stream[IO, Byte] =
        Stream.emits(Seq("hello")).repeatN(256).through(fs2.text.utf8.encode).covary[IO]
      val expected = "hello" * 256

      val uri = Uri
        .fromString(url(server.address, "/echo"))
        .toOption
        .get
      val request = POST(body, uri)
      for {
        r1 <- client.fetchAs[String](request)
        r2 <- client.fetchAs[String](request)
      } yield assertEquals(expected, r1) && assertEquals(expected, r2)
  }
}
