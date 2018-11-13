package org.http4s.server.middleware

import cats.effect._
import org.http4s.{Http4sSpec, HttpRoutes, Request, Status}
import org.http4s.server.{MockRoute, Router}
import org.http4s.Uri.uri

class AutoSlashSpec extends Http4sSpec {

  val route = MockRoute.route()

  val pingRoutes = {
    import org.http4s.dsl.io._
    HttpRoutes.of[IO] {
      case GET -> Root / "ping" => Ok()
    }
  }

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

    "Work when nested in Router" in {
      // See https://github.com/http4s/http4s/issues/1378
      val router = Router("/public" -> AutoSlash(pingRoutes))
      router.orNotFound(Request[IO](uri = uri("/public/ping"))) must returnStatus(Status.Ok)
      router.orNotFound(Request[IO](uri = uri("/public/ping/"))) must returnStatus(Status.Ok)
    }

    "Work when Router is nested in AutoSlash" in {
      // See https://github.com/http4s/http4s/issues/1947
      val router = AutoSlash(Router("/public" -> pingRoutes))
      router.orNotFound(Request[IO](uri = uri("/public/ping"))) must returnStatus(Status.Ok)
      router.orNotFound(Request[IO](uri = uri("/public/ping/"))) must returnStatus(Status.Ok)
    }
  }
}
