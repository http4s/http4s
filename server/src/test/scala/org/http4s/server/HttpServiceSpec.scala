package org.http4s
package server

import cats.effect._
import cats.implicits._
import cats._

class HttpServiceSpec extends Http4sSpec {

  val srvc1 = HttpService[IO] {
    case req if req.pathInfo == "/match" =>
      Response[IO](Status.Ok).withBody("match")

    case req if req.pathInfo == "/conflict" =>
      Response[IO](Status.Ok).withBody("srvc1conflict")

    case req if req.pathInfo == "/notfound" =>
      Response[IO](Status.NotFound).withBody("notfound")
  }

  val srvc2 = HttpService[IO] {
    case req if req.pathInfo == "/srvc2" =>
      Response[IO](Status.Ok).withBody("srvc2")

    case req if req.pathInfo == "/conflict" =>
      Response[IO](Status.Ok).withBody("srvc2conflict")
  }

  val aggregate1 = srvc1 |+| srvc2

  "HttpService" should {
    "Return a valid Response from the first service of an aggregate" in {
      aggregate1.orNotFound(Request[IO](uri = uri("/match"))) must returnBody("match")
    }

    "Return a custom NotFound from the first service of an aggregate" in {
      aggregate1.orNotFound(Request[IO](uri = uri("/notfound"))) must returnBody("notfound")
    }

    "Accept the first matching route in the case of overlapping paths" in {
      aggregate1.orNotFound(Request[IO](uri = uri("/conflict"))) must returnBody("srvc1conflict")
    }

    "Fall through the first service that doesn't match to a second matching service" in {
      aggregate1.orNotFound(Request[IO](uri = uri("/srvc2"))) must returnBody("srvc2")
    }

    "Properly fall through two aggregated service if no path matches" in {
      aggregate1.apply(Request[IO](uri = uri("/wontMatch"))) must returnValue(Pass[IO]())
    }
  }
}
