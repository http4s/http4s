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

class ContextRouterSpec extends Http4sSpec with Http4sLegacyMatchersIO {
  val numbers = ContextRoutes.of[Unit, IO] {
    case GET -> Root / "1" as _ =>
      Ok("one")
  }
  val numbers2 = ContextRoutes.of[Unit, IO] {
    case GET -> Root / "1" as _ =>
      Ok("two")
  }

  val letters = ContextRoutes.of[Unit, IO] {
    case GET -> Root / "/b" as _ =>
      Ok("bee")
  }
  val shadow = ContextRoutes.of[Unit, IO] {
    case GET -> Root / "shadowed" as _ =>
      Ok("visible")
  }
  val root = ContextRoutes.of[Unit, IO] {
    case GET -> Root / "about" as _ =>
      Ok("about")
    case GET -> Root / "shadow" / "shadowed" as _ =>
      Ok("invisible")
  }

  val notFound = ContextRoutes.of[Unit, IO] {
    case _ as _ => NotFound("Custom NotFound")
  }

  def middleware(routes: ContextRoutes[Unit, IO]): ContextRoutes[Unit, IO] =
    Kleisli((r: ContextRequest[IO, Unit]) =>
      if (r.req.uri.query.containsQueryParam("block"))
        OptionT.liftF(Ok(r.req.uri.path.renderString))
      else routes(r))

  val service = ContextRouter[IO, Unit](
    "/numbers" -> numbers,
    "/numb" -> middleware(numbers2),
    "/" -> root,
    "/shadow" -> shadow,
    "/letters" -> letters
  )

  "A router" should {
    "translate mount prefixes" in {
      service.orNotFound(ContextRequest((), Request[IO](GET, uri"/numbers/1"))) must returnBody(
        "one")
      service.orNotFound(ContextRequest((), Request[IO](GET, uri"/numb/1"))) must returnBody("two")
      service.orNotFound(ContextRequest((), Request[IO](GET, uri"/numbe?block"))) must returnStatus(
        NotFound)
    }

    "require the correct prefix" in {
      val resp =
        service.orNotFound(ContextRequest((), Request[IO](GET, uri"/letters/1"))).unsafeRunSync()
      resp must not(haveBody("bee"))
      resp must not(haveBody("one"))
      resp must haveStatus(NotFound)
    }

    "support root mappings" in {
      service.orNotFound(ContextRequest((), Request[IO](GET, uri"/about"))) must returnBody("about")
    }

    "match longer prefixes first" in {
      service.orNotFound(
        ContextRequest((), Request[IO](GET, uri"/shadow/shadowed"))) must returnBody("visible")
    }

    "404 on unknown prefixes" in {
      service.orNotFound(ContextRequest((), Request[IO](GET, uri"/symbols/~"))) must returnStatus(
        NotFound)
    }

    "Allow passing through of routes with identical prefixes" in {
      ContextRouter[IO, Unit]("" -> letters, "" -> numbers)
        .orNotFound(ContextRequest((), Request[IO](GET, uri"/1"))) must returnBody("one")
    }

    "Serve custom NotFound responses" in {
      ContextRouter[IO, Unit]("/foo" -> notFound).orNotFound(
        ContextRequest((), Request[IO](uri = uri"/foo/bar"))) must returnBody("Custom NotFound")
    }

    "Return the fallthrough response if no route is found" in {
      val router = ContextRouter[IO, Unit]("/foo" -> notFound)
      router(ContextRequest((), Request[IO](uri = uri"/bar"))).value must returnValue(
        Option.empty[Response[IO]])
    }
  }
}
