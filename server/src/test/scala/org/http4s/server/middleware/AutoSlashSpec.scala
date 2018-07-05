package org.http4s.server.middleware

import cats.effect._
import org.http4s.{Http4sSpec, HttpService, Request, Response, Status}
import org.http4s.server.{MockRoute, Router}

class AutoSlashSpec extends Http4sSpec {

  val route = MockRoute.route()

  "AutoSlash" should {
    "Auto remove a trailing slash" in {
      val req = Request[IO](uri = uri("/ping/"))
      route.orNotFound(req) must returnStatus(Status.NotFound)
      AutoSlash(route).orNotFound(req) must returnStatus(Status.Ok)
    }

    "Match a route defined with a slash" in {
      AutoSlash(route).orNotFound(Request[IO](uri = uri("/withslash"))) must returnStatus(Status.Ok)
      AutoSlash(route).orNotFound(Request[IO](uri = uri("/withslash/"))) must returnStatus(
        Status.Accepted)
    }

    "Respect an absent trailing slash" in {
      val req = Request[IO](uri = uri("/ping"))
      route.orNotFound(req) must returnStatus(Status.Ok)
      AutoSlash(route).orNotFound(req) must returnStatus(Status.Ok)
    }

    "Not crash on empty path" in {
      val req = Request[IO](uri = uri(""))
      AutoSlash(route).orNotFound(req) must returnStatus(Status.NotFound)
    }

    "Work with prefixed routes" in {
      // See https://github.com/http4s/http4s/issues/1378
      val service = {
        import org.http4s.dsl.io._
        HttpService[IO] {
          case GET -> Root / "ping" =>
            IO.pure(Response[IO](Status.Ok))
        }
      }
      val router = Router("/public" -> AutoSlash(service))
      val req = Request[IO](uri = uri("/public/ping/"))
      router.orNotFound(req) must returnStatus(Status.Ok)
    }
  }
}
