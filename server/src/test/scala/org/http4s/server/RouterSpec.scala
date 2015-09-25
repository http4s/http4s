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
    case _ =>
      Response(NotFound).withBody("custom 404")
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

  val notFound = HttpService.notFound.run.as[String].run

  "A router" should {
    "translate mount prefixes" in {
      service.apply(Request(GET, uri("/numbers/1"))).run.as[String].run must equal ("one")
    }

    "require the correct prefix" in {
      val resp = service.apply(Request(GET, uri("/letters/1"))).run
      resp.as[String].run must not equal ("bee")
      resp.as[String].run must not equal ("one")
      resp.status must equal (NotFound)
    }

    "support root mappings" in {
      service.apply(Request(GET, uri("/about"))).run.as[String].run must equal ("about")
    }

    "match longer prefixes first" in {
      service.apply(Request(GET, uri("/shadow/shadowed"))).run.as[String].run must equal ("visible")
    }

    "Offer the default 404 on unknown prefixes" in {
      service.apply(Request(GET, uri("/symbols/~"))).run.as[String].run must equal (notFound)
    }

    "Preserve custom 404 responses under the right path" in {
      service.apply(Request(GET, uri("/letters/a"))).run.as[String].run must equal ("custom 404")
    }

    "Allow passing through of routes with identical prefixes" in {
      Router("" -> letters, "" -> numbers).apply(Request(GET, uri("/1")))
        .run.as[String].run must equal ("one")
    }

  }
}
