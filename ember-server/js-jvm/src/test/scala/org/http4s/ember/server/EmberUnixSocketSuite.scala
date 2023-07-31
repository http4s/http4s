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
import fs2.io.file._
import fs2.io.net.unixsocket.UnixSocketAddress
import fs2.io.net.unixsocket.UnixSockets
import org.http4s._
import org.http4s.client.middleware.UnixSocket
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.core.h2.H2Keys

import scala.concurrent.duration._

class EmberUnixSocketSuite extends Http4sSuite {

  def run(
      setupServer: EmberServerBuilder[IO] => EmberServerBuilder[IO],
      setupClient: EmberClientBuilder[IO] => EmberClientBuilder[IO],
      setupRequest: Request[IO] => Request[IO],
  ): IO[Unit] = {

    val msg = "Hello Unix Sockets!"

    def app = HttpApp.liftF(Response[IO](Status.Ok).withEntity(msg).pure[IO])

    Files[IO].tempFile(None, "", "sock", None).use { path =>
      val localSocket = UnixSocketAddress(path.toString)

      val server = setupServer(
        EmberServerBuilder
          .default[IO]
          .withUnixSocketConfig(UnixSockets[IO], localSocket)
          .withShutdownTimeout(1.second)
          .withHttpApp(app)
      ).build

      val client = setupClient(
        EmberClientBuilder
          .default[IO]
          .withUnixSockets(UnixSockets[IO])
      ).build.map(UnixSocket(localSocket))

      val request = setupRequest(Request[IO](Method.GET))

      Files[IO].deleteIfExists(path) *>
        (server *> client).use { client =>
          IO.sleep(4.seconds) *>
            client.expect[String](request).assertEquals(msg) *>
            client.expect[String](request).assertEquals(msg)
        }
    }
  }

  test("http/1.1") {
    run(identity(_), identity(_), identity(_))
  }

  test("http/2") {
    run(_.withHttp2, _.withHttp2, _.withAttribute(H2Keys.Http2PriorKnowledge, ()))
  }

}
