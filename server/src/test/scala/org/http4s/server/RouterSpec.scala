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
      service.apply(Request(GET, uri("/numbers/1"))).run.as[String].run must_== ("one")
    }

    "require the correct prefix" in {
      val resp = service.apply(Request(GET, uri("/letters/1"))).run
      resp.as[String].run must_!= ("bee")
      resp.as[String].run must_!= ("one")
      resp.status must_== (NotFound)
    }

    "support root mappings" in {
      service.apply(Request(GET, uri("/about"))).run.as[String].run must_== ("about")
    }

    "match longer prefixes first" in {
      service.apply(Request(GET, uri("/shadow/shadowed"))).run.as[String].run must_== ("visible")
    }

    "404 on unknown prefixes" in {
      service.apply(Request(GET, uri("/symbols/~"))).run.status must_== (NotFound)
    }

    "Allow passing through of routes with identical prefixes" in {
      Router("" -> letters, "" -> numbers).apply(Request(GET, uri("/1")))
        .run.as[String].run must_== ("one")
    }

    "Serve custom NotFound responses" in {
      Router("/foo" -> notFound).apply(Request(uri = uri("/foo/bar"))).run.as[String].run must_== ("Custom NotFound")
    }

    "Return the fallthrough response if no route is found" in {
      val resp = Router("/foo" -> notFound).apply(Request(uri = uri("/bar"))).run
      Fallthrough[Response].isFallthrough(resp) must beTrue
    }
  }
}
