package org.http4s
package server

import org.http4s.dsl._

class RouterSpec extends Http4sSpec {
  val numbers: HttpService = HttpService {
    case GET -> Root / "1" =>
      Ok("one")
  }
  val letters: HttpService = HttpService {
    case GET -> Root / "/b" =>
      Ok("bee")
  }
  val shadow: HttpService = HttpService {
    case GET -> Root / "shadowed" =>
      Ok("visible")
  }
  val root: HttpService  = HttpService {
    case GET -> Root / "about" =>
      Ok("about")
    case GET -> Root / "shadow" / "shadowed" =>
      Ok("invisible")
  }

  val notFound: HttpService = HttpService {
    case _ => NotFound("Custom NotFound")
  }

  val service = Router(
    "/numbers" -> numbers,
    "/" -> root,
    "/shadow" -> shadow,
    "/letters" -> letters
  )

  "A router" should {
    "translate mount prefixes" in {
      service.apply(Request(GET, uri("/numbers/1"))) must runToBody("one")
    }

    "require the correct prefix" in {
      val resp = service.apply(Request(GET, uri("/letters/1"))).run
      resp.as[String].run must_!= ("bee")
      resp.as[String].run must_!= ("one")
      resp.status must_== (NotFound)
    }

    "support root mappings" in {
      service.apply(Request(GET, uri("/about"))) must runToBody("about")
    }

    "match longer prefixes first" in {
      service.apply(Request(GET, uri("/shadow/shadowed"))) must runToBody("visible")
    }

    "404 on unknown prefixes" in {
      service.apply(Request(GET, uri("/symbols/~"))) must runToStatus (NotFound)
    }

    "Allow passing through of routes with identical prefixes" in {
      Router("" -> letters, "" -> numbers).apply(Request(GET, uri("/1"))) must runToBody("one")
    }

    "Serve custom NotFound responses" in {
      Router("/foo" -> notFound).apply(Request(uri = uri("/foo/bar"))) must runToBody("Custom NotFound")
    }

    "Return the tagged NotFound response if no route is found" in {
      val resp = Router("/foo" -> notFound).apply(Request(uri = uri("/bar"))).run
      resp.attributes.contains(Fallthrough.fallthroughKey) must_== (true)
    }

  }
}
