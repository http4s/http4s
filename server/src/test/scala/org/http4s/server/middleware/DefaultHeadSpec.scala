package org.http4s
package server
package middleware

import cats.effect._
import fs2.Stream._
import org.http4s.dsl.io._

class DefaultHeadSpec extends Http4sSpec {
  val service = DefaultHead[IO](HttpService[IO] {
    case GET -> Root / "hello" =>
      Ok("hello")

    case GET -> Root / "special" =>
      Ok().putHeaders(Header("X-Handled-By", "GET"))

    case HEAD -> Root / "special" =>
      Ok().putHeaders(Header("X-Handled-By", "HEAD"))
  })

  "DefaultHead" should {
    "honor HEAD routes" in {
      val req = Request[IO](Method.HEAD, uri = uri("/special"))
      service.orNotFound(req).map(_.headers.get("X-Handled-By".ci).map(_.value)) must returnValue(
        Some("HEAD"))
    }

    "return truncated body of corresponding GET on fallthrough" in {
      val req = Request[IO](Method.HEAD, uri = uri("/hello"))
      service.orNotFound(req) must returnBody("")
    }

    "retain all headers of corresponding GET on fallthrough" in {
      val get = Request[IO](Method.GET, uri = uri("/hello"))
      val head = get.withMethod(Method.HEAD)
      service.orNotFound(get).map(_.headers).unsafeRunSync() must_== service
        .orNotFound(head)
        .map(_.headers)
        .unsafeRunSync()
    }

    "allow GET body to clean up on fallthrough" in {
      var cleanedUp = false
      val service = DefaultHead[IO](HttpService[IO] {
        case GET -> _ =>
          val body: EntityBody[IO] = eval_(IO(cleanedUp = true))
          Ok(body)
      })
      service.orNotFound(Request[IO](Method.HEAD)).flatMap(_.as[String]).unsafeRunSync()
      cleanedUp must beTrue
    }
  }
}
