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
import com.comcast.ip4s.UnixSocketAddress
import fs2.io.file._
import org.http4s._
import org.http4s.client.middleware.UnixSocket
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.h2.H2Keys.Http2PriorKnowledge

import scala.concurrent.duration._

class EmberUnixSocketSuite extends Http4sSuite {

  def run(
      setupServer: EmberServerBuilder[IO] => EmberServerBuilder[IO],
      setupClient: EmberClientBuilder[IO] => EmberClientBuilder[IO],
      setupRequest: Request[IO] => Request[IO],
      checkRequest: Request[IO] => Unit,
  ): IO[Unit] = {

    val msg = "Hello Unix Sockets!"

    // Requests are checked after the exchange, so a failed assertion isn't turned into a 500
    def app(seen: Ref[IO, List[Request[IO]]]) =
      HttpApp[IO](req => seen.update(req :: _).as(Response[IO](Status.Ok).withEntity(msg)))

    (
      Files[IO].tempFile(None, "", "sock", None),
      Ref[IO].of(List.empty[Request[IO]]).toResource,
    ).tupled.use { case (path, seen) =>
      val localSocket = UnixSocketAddress(path.toString)

      val server = setupServer(
        EmberServerBuilder
          .default[IO]
          .withUnixSocketConfig(localSocket)
          .withShutdownTimeout(1.second)
          .withHttpApp(app(seen))
      ).build

      val client = setupClient(
        EmberClientBuilder
          .default[IO]
      ).build.map(UnixSocket(localSocket))

      val request = setupRequest(Request[IO](Method.GET))

      Files[IO].deleteIfExists(path) *>
        (server *> client).use { client =>
          client.expect[String](request).assertEquals(msg) *>
            client.expect[String](request).assertEquals(msg)
        } *>
        seen.get.map { reqs =>
          assertEquals(reqs.size, 2)
          reqs.foreach(checkRequest)
        }
    }
  }

  // Unix sockets have no IP addresses, so neither HTTP/1.1 nor HTTP/2 attach ConnectionInfo
  private def assertNoConnectionInfo(req: Request[IO]): Unit =
    assert(!req.attributes.contains(Request.Keys.ConnectionInfo), "unexpected ConnectionInfo")

  test("http/1.1") {
    run(identity(_), identity(_), identity(_), assertNoConnectionInfo)
  }

  test("http/2".flaky) {
    run(
      _.withHttp2,
      _.withHttp2,
      _.withAttribute(Http2PriorKnowledge, ()),
      { req =>
        assertEquals(req.httpVersion, HttpVersion.`HTTP/2`)
        assertNoConnectionInfo(req)
      },
    )
  }

}
