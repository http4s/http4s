package org.http4s
package server

import cats.Monad
import cats.effect._
import org.http4s.dsl.io._

class RouterSpec extends Http4sSpec {
  val numbers = HttpService[IO] {
    case GET -> Root / "1" =>
      Ok("one")
  }
  val letters = HttpService[IO] {
    case GET -> Root / "/b" =>
      Ok("bee")
  }
  val shadow = HttpService[IO] {
    case GET -> Root / "shadowed" =>
      Ok("visible")
  }
  val root = HttpService[IO] {
    case GET -> Root / "about" =>
      Ok("about")
    case GET -> Root / "shadow" / "shadowed" =>
      Ok("invisible")
  }

  val notFound = HttpService[IO] {
    case _ => NotFound("Custom NotFound")
  }

  val service = Router[IO](
    "/numbers" -> numbers,
    "/" -> root,
    "/shadow" -> shadow,
    "/letters" -> letters
  )

  "A router" should {
    "translate mount prefixes" in {
      service.orNotFound(Request[IO](GET, uri("/numbers/1"))) must returnBody("one")
    }

    "require the correct prefix" in {
      val resp = service.orNotFound(Request[IO](GET, uri("/letters/1"))).unsafeRunSync()
      resp must not(haveBody("bee"))
      resp must not(haveBody("one"))
      resp must haveStatus(NotFound)
    }

    "support root mappings" in {
      service.orNotFound(Request[IO](GET, uri("/about"))) must returnBody("about")
    }

    "match longer prefixes first" in {
      service.orNotFound(Request[IO](GET, uri("/shadow/shadowed"))) must returnBody("visible")
    }

    "404 on unknown prefixes" in {
      service.orNotFound(Request[IO](GET, uri("/symbols/~"))) must returnStatus(NotFound)
    }

    "Allow passing through of routes with identical prefixes" in {
      Router[IO]("" -> letters, "" -> numbers)
        .orNotFound(Request[IO](GET, uri("/1"))) must returnBody("one")
    }

    "Serve custom NotFound responses" in {
      Router[IO]("/foo" -> notFound).orNotFound(Request[IO](uri = uri("/foo/bar"))) must returnBody(
        "Custom NotFound")
    }

    "Return the fallthrough response if no route is found" in {
      val router = Router[IO]("/foo" -> notFound)
      router(Request[IO](uri = uri("/bar"))).value must returnValue(None)
    }

    "Only require a Monad instance for a given F[_]" in {
      def router[F[_]: Monad]: HttpService[F] = Router(
        "/" -> HttpService.empty[F]
      )
      router[IO] must haveClass[HttpService[IO]]
    }
  }
}
