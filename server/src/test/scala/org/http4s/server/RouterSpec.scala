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
      service.orNotFound(Request(GET, uri("/numbers/1"))) must returnBody("one")
    }

    "require the correct prefix" in {
      val resp = service.orNotFound(Request(GET, uri("/letters/1"))).unsafeRun
      resp must not(haveBody("bee"))
      resp must not(haveBody("one"))
      resp must haveStatus(NotFound)
    }

    "support root mappings" in {
      service.orNotFound(Request(GET, uri("/about"))) must returnBody("about")
    }

    "match longer prefixes first" in {
      service.orNotFound(Request(GET, uri("/shadow/shadowed"))) must returnBody("visible")
    }

    "404 on unknown prefixes" in {
      service.orNotFound(Request(GET, uri("/symbols/~"))) must returnStatus(NotFound)
    }

    "Allow passing through of routes with identical prefixes" in {
      Router("" -> letters, "" -> numbers).orNotFound(Request(GET, uri("/1"))) must returnBody("one")
    }

    "Serve custom NotFound responses" in {
      Router("/foo" -> notFound).orNotFound(Request(uri = uri("/foo/bar"))) must returnBody("Custom NotFound")
    }

    "Return the fallthrough response if no route is found" in {
      Router("/foo" -> notFound)(Request(uri = uri("/bar"))) must returnValue(Pass)
    }
  }
}
