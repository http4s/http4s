package org.http4s
package server

import cats.effect._
import cats.implicits._
import org.http4s.Uri.uri

class HttpRoutesSpec extends Http4sSpec {

  val routes1 = HttpRoutes.of[IO] {
    case req if req.pathInfo == "/match" =>
      Response[IO](Status.Ok).withEntity("match").pure[IO]

    case req if req.pathInfo == "/conflict" =>
      Response[IO](Status.Ok).withEntity("routes1conflict").pure[IO]

    case req if req.pathInfo == "/notfound" =>
      Response[IO](Status.NotFound).withEntity("notfound").pure[IO]
  }

  val routes2 = HttpRoutes.of[IO] {
    case req if req.pathInfo == "/routes2" =>
      Response[IO](Status.Ok).withEntity("routes2").pure[IO]

    case req if req.pathInfo == "/conflict" =>
      Response[IO](Status.Ok).withEntity("routes2conflict").pure[IO]
  }

  val aggregate1 = routes1 <+> routes2

  "HttpRoutes" should {
    "Return a valid Response from the first service of an aggregate" in {
      aggregate1.orNotFound(Request[IO](uri = uri("/match"))) must returnBody("match")
    }

    "Return a custom NotFound from the first service of an aggregate" in {
      aggregate1.orNotFound(Request[IO](uri = uri("/notfound"))) must returnBody("notfound")
    }

    "Accept the first matching route in the case of overlapping paths" in {
      aggregate1.orNotFound(Request[IO](uri = uri("/conflict"))) must returnBody("routes1conflict")
    }

    "Fall through the first service that doesn't match to a second matching service" in {
      aggregate1.orNotFound(Request[IO](uri = uri("/routes2"))) must returnBody("routes2")
    }

    "Properly fall through two aggregated service if no path matches" in {
      aggregate1.apply(Request[IO](uri = uri("/wontMatch"))).value must returnValue(None)
    }
  }
}
