/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server

import cats.data.{Kleisli, OptionT}
import cats.effect._
import org.http4s.dsl.io._
import org.http4s.testing.Http4sLegacyMatchersIO

class RouterSpec extends Http4sSpec with Http4sLegacyMatchersIO {
  val numbers = HttpRoutes.of[IO] { case GET -> Root / "1" =>
    Ok("one")
  }
  val numbers2 = HttpRoutes.of[IO] { case GET -> Root / "1" =>
    Ok("two")
  }

  val letters = HttpRoutes.of[IO] { case GET -> Root / "/b" =>
    Ok("bee")
  }
  val shadow = HttpRoutes.of[IO] { case GET -> Root / "shadowed" =>
    Ok("visible")
  }
  val root = HttpRoutes.of[IO] {
    case GET -> Root / "about" =>
      Ok("about")
    case GET -> Root / "shadow" / "shadowed" =>
      Ok("invisible")
  }

  val notFound = HttpRoutes.of[IO] { case _ =>
    NotFound("Custom NotFound")
  }

  def middleware(routes: HttpRoutes[IO]): HttpRoutes[IO] =
    Kleisli((r: Request[IO]) =>
      if (r.uri.query.containsQueryParam("block")) OptionT.liftF(Ok(r.uri.path)) else routes(r))

  val service = Router[IO](
    "/numbers" -> numbers,
    "/numb" -> middleware(numbers2),
    "/" -> root,
    "/shadow" -> shadow,
    "/letters" -> letters
  )

  "A router" should {
    "translate mount prefixes" in {
      service.orNotFound(Request[IO](GET, uri"/numbers/1")) must returnBody("one")
      service.orNotFound(Request[IO](GET, uri"/numb/1")) must returnBody("two")
      service.orNotFound(Request[IO](GET, uri"/numbe?block")) must returnStatus(NotFound)
    }

    "require the correct prefix" in {
      val resp = service.orNotFound(Request[IO](GET, uri"/letters/1")).unsafeRunSync()
      resp must not(haveBody("bee"))
      resp must not(haveBody("one"))
      resp must haveStatus(NotFound)
    }

    "support root mappings" in {
      service.orNotFound(Request[IO](GET, uri"/about")) must returnBody("about")
    }

    "match longer prefixes first" in {
      service.orNotFound(Request[IO](GET, uri"/shadow/shadowed")) must returnBody("visible")
    }

    "404 on unknown prefixes" in {
      service.orNotFound(Request[IO](GET, uri"/symbols/~")) must returnStatus(NotFound)
    }

    "Allow passing through of routes with identical prefixes" in {
      Router[IO]("" -> letters, "" -> numbers)
        .orNotFound(Request[IO](GET, uri"/1")) must returnBody("one")
    }

    "Serve custom NotFound responses" in {
      Router[IO]("/foo" -> notFound).orNotFound(Request[IO](uri = uri"/foo/bar")) must returnBody(
        "Custom NotFound")
    }

    "Return the fallthrough response if no route is found" in {
      val router = Router[IO]("/foo" -> notFound)
      router(Request[IO](uri = uri"/bar")).value must returnValue(Option.empty[Response[IO]])
    }
  }
}
