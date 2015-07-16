package org.http4s
package server

import org.http4s.Method._
import org.http4s.Status._

class RouterSpec extends Http4sSpec {
  val numbers: HttpService = HttpService {
    case req if req.pathInfo == "/1" =>
      Response(Ok).withBody("one")
  }
  val letters: HttpService = HttpService {
    case req if req.pathInfo == "/b" =>
      Response(Ok).withBody("bee")
  }
  val shadow: HttpService = HttpService {
    case req if req.pathInfo == "/shadowed" =>
      Response(Ok).withBody("visible")
  }
  val root: HttpService  = HttpService {
    case req if req.pathInfo == "/about" =>
      Response(Ok).withBody("about")
    case req if req.pathInfo == "/shadow/shadowed" =>
      Response(Ok).withBody("invisible")
  }

  val service = Router(
    "/numbers" -> numbers,
    "/" -> root,
    "/shadow" -> shadow,
    "/letters" -> letters
  )

  "A router" should {
    "translate mount prefixes" in {
      service(Request(GET, uri("/numbers/1"))).run.as[String].run must equal ("one")
    }

    "require the correct prefix" in {
      service(Request(GET, uri("/letters/1"))).run.status must equal (NotFound)
    }

    "support root mappings" in {
      service(Request(GET, uri("/about"))).run.as[String].run must equal ("about")
    }

    "match longer prefixes first" in {
      service(Request(GET, uri("/shadow/shadowed"))).run.as[String].run must equal ("visible")
    }

    "404 on unknown prefixes" in {
      service(Request(GET, uri("/symbols/~"))).run.status must equal (NotFound)
    }
  }
}
