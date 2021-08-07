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
package server
package middleware

import cats.effect._
import cats.implicits._
import fs2._
import fs2.Stream._
import java.nio.charset.StandardCharsets
import org.http4s.Method._
import org.http4s.Status._
import org.http4s.syntax.all._
import org.http4s.server.middleware.EntityLimiter.EntityTooLarge

class EntityLimiterSuite extends Http4sSuite {
  val routes = HttpRoutes.of[IO] {
    case r if r.pathInfo == path"/echo" => r.decode[String](Response[IO](Ok).withEntity(_).pure[IO])
  }

  val b = chunk(Chunk.array("hello".getBytes(StandardCharsets.UTF_8)))

  test("Allow reasonable entities") {
    EntityLimiter(routes, 100)
      .apply(Request[IO](POST, uri"/echo", body = b))
      .map(_ => -1)
      .value
      .assertEquals(Some(-1))
  }

  test("Limit the maximum size of an EntityBody") {
    EntityLimiter(routes, 3)
      .apply(Request[IO](POST, uri"/echo", body = b))
      .map(_ => -1L)
      .value
      .handleError { case EntityTooLarge(i) => Some(i) }
      .assertEquals(Some(3L))
  }

  test("Chain correctly with other HttpRoutes") {
    val routes2 = HttpRoutes.of[IO] {
      case r if r.pathInfo == path"/echo2" =>
        r.decode[String](Response[IO](Ok).withEntity(_).pure[IO])
    }

    val st = EntityLimiter(routes, 3) <+> routes2

    st.apply(Request[IO](POST, uri"/echo2", body = b))
      .map(_ => -1)
      .value
      .assertEquals(Some(-1)) *>
      st.apply(Request[IO](POST, uri"/echo", body = b))
        .map(_ => -1L)
        .value
        .handleError { case EntityTooLarge(i) => Some(i) }
        .assertEquals(Some(3L))
  }

  test("Be created via the httpRoutes constructor") {
    EntityLimiter
      .httpRoutes(routes, 3)
      .apply(Request[IO](POST, uri"/echo", body = b))
      .map(_ => -1L)
      .value
      .handleError { case EntityTooLarge(i) => Some(i) }
      .assertEquals(Some(3L))
  }

  test("Be created via the httpRoutes constructor") {
    val app: HttpApp[IO] = routes.orNotFound

    EntityLimiter
      .httpApp(app, 3L)
      .apply(Request[IO](POST, uri"/echo", body = b))
      .map(_ => -1L)
      .handleError { case EntityTooLarge(i) => i }
      .assertEquals(3L)
  }
}
