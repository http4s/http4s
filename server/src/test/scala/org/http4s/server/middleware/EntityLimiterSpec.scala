/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
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
import org.http4s.Uri.uri
import org.http4s.server.middleware.EntityLimiter.EntityTooLarge
import org.http4s.testing.Http4sLegacyMatchersIO

class EntityLimiterSpec extends Http4sSpec with Http4sLegacyMatchersIO {
  val routes = HttpRoutes.of[IO] {
    case r if r.uri.path == "/echo" => r.decode[String](Response[IO](Ok).withEntity(_).pure[IO])
  }

  val b = chunk(Chunk.bytes("hello".getBytes(StandardCharsets.UTF_8)))

  "EntityLimiter" should {
    "Allow reasonable entities" in {
      EntityLimiter(routes, 100)
        .apply(Request[IO](POST, uri("/echo"), body = b))
        .map(_ => -1)
        .value must returnValue(Some(-1))
    }

    "Limit the maximum size of an EntityBody" in {
      EntityLimiter(routes, 3)
        .apply(Request[IO](POST, uri("/echo"), body = b))
        .map(_ => -1L)
        .value
        .handleError { case EntityTooLarge(i) => Some(i) } must returnValue(Some(3))
    }

    "Chain correctly with other HttpRoutes" in {
      val routes2 = HttpRoutes.of[IO] {
        case r if r.uri.path == "/echo2" =>
          r.decode[String](Response[IO](Ok).withEntity(_).pure[IO])
      }

      val st = EntityLimiter(routes, 3) <+> routes2

      st.apply(Request[IO](POST, uri("/echo2"), body = b))
        .map(_ => -1)
        .value must returnValue(Some(-1))

      st.apply(Request[IO](POST, uri("/echo"), body = b))
        .map(_ => -1L)
        .value
        .handleError { case EntityTooLarge(i) => Some(i) } must returnValue(Some(3L))
    }

    "Be created via the httpRoutes constructor" in {
      EntityLimiter
        .httpRoutes(routes, 3)
        .apply(Request[IO](POST, uri("/echo"), body = b))
        .map(_ => -1L)
        .value
        .handleError { case EntityTooLarge(i) => Some(i) } must returnValue(Some(3))
    }

    "Be created via the httpRoutes constructor" in {
      val app: HttpApp[IO] = routes.orNotFound

      EntityLimiter
        .httpApp(app, 3)
        .apply(Request[IO](POST, uri("/echo"), body = b))
        .map(_ => -1L)
        .handleError { case EntityTooLarge(i) => i } must returnValue(3)
    }
  }
}
