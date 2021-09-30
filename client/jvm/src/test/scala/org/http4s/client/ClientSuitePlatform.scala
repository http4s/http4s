/*
 * Copyright 2014 http4s.org
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

package org.http4s
package client

import cats.effect._
import cats.syntax.all._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Host
import org.http4s.server.middleware.VirtualHost
import org.http4s.server.middleware.VirtualHost.exact
import org.http4s.syntax.all._

class ClientSpecPlatform extends Http4sSuite with Http4sDsl[IO] {
  private val app = HttpApp[IO] { case r =>
    Response[IO](Ok).withEntity(r.body).pure[IO]
  }
  val client: Client[IO] = Client.fromHttpApp(app)

  test("mock client should cooperate with the VirtualHost server middleware") {
    val routes = HttpRoutes.of[IO] { case r =>
      Ok(r.headers.get[Host].map(_.value).getOrElse("None"))
    }

    val hostClient = Client.fromHttpApp(VirtualHost(exact(routes, "http4s.org")).orNotFound)

    hostClient
      .expect[String](Request[IO](GET, uri"https://http4s.org/"))
      .assertEquals("http4s.org")
  }

}
