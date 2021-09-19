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
import org.http4s._
import org.http4s.ember.client.EmberClientBuilder
import fs2.io.net.unixsocket.{UnixSocketAddress, UnixSockets}
import org.http4s.client.middleware.UnixSocket
import scala.concurrent.duration._

class EmberUnixSocketSuite extends Http4sSuite {

  test("Should be able to connect to a server created") {
    def app = HttpApp.liftF(Response[IO](Status.Ok).withEntity("Hello Unix Sockets!").pure[IO])
    val test = for {
      id <- Resource.eval(IO(java.util.UUID.randomUUID().toString()))
      localSocket = UnixSocketAddress("/tmp/" ++ id ++ ".sock")
      _ <- EmberServerBuilder
        .default[IO]
        .withUnixSocketConfig(UnixSockets[IO], localSocket)
        .withHttpApp(app)
        .build
      client <- EmberClientBuilder.default[IO].build.map(UnixSocket(localSocket))
      _ <- Resource.eval(IO.sleep(1.second))
      resp <- client.run(Request[IO](Method.GET))
      body <- Resource.eval(resp.bodyText.compile.string)

    } yield {
      assertEquals(resp.status, Status.Ok)
      assertEquals(body, "Hello Unix Sockets!")
    }
    test.use(_ => ().pure[IO])
  }

}
