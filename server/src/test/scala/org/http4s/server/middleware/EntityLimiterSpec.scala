package org.http4s
package server
package middleware

import cats.effect._
import cats.implicits._
import fs2._
import fs2.Stream._
import java.nio.charset.StandardCharsets
import org.http4s.server.middleware.EntityLimiter.EntityTooLarge
import org.http4s.Method._
import org.http4s.Status._

class EntityLimiterSpec extends Http4sSpec {

  val s = HttpService[IO] {
    case r: Request[IO] if r.uri.path == "/echo" => r.decode[String](Response[IO](Ok).withBody)
  }

  val b = chunk(Chunk.bytes("hello".getBytes(StandardCharsets.UTF_8)))

  "EntityLimiter" should {

    "Allow reasonable entities" in {
      EntityLimiter(s, 100)
        .apply(Request[IO](POST, uri("/echo"), body = b))
        .map(_ => -1) must returnValue(-1)
    }

    "Limit the maximum size of an EntityBody" in {
      EntityLimiter(s, 3)
        .apply(Request[IO](POST, uri("/echo"), body = b))
        .map(_ => -1L)
        .handleError { case EntityTooLarge(i) => i } must returnValue(3)
    }

    "Chain correctly with other HttpServices" in {
      val s2 = HttpService[IO] {
        case r: Request[IO] if r.uri.path == "/echo2" => r.decode[String](Response[IO](Ok).withBody)
      }

      val st = EntityLimiter(s, 3) |+| s2
      (st
        .apply(Request[IO](POST, uri("/echo2"), body = b))
        .map(_ => -1)
        must returnValue(-1))
      (st
        .apply(Request[IO](POST, uri("/echo"), body = b))
        .map(_ => -1L)
        .handleError { case EntityTooLarge(i) => i }
        must returnValue(3L))
    }
  }

}
