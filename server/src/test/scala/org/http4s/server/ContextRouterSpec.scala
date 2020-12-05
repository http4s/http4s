/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package server

import cats.data.{Kleisli, OptionT}
import cats.effect._
import org.http4s.dsl.io._
import org.http4s.testing.Http4sLegacyMatchersIO

class ContextRouterSpec extends Http4sSpec with Http4sLegacyMatchersIO {
  val numbers = ContextRoutes.of[Unit, IO] { case GET -> Root / "1" as _ =>
    Ok("one")
  }
  val numbers2 = ContextRoutes.of[Unit, IO] { case GET -> Root / "1" as _ =>
    Ok("two")
  }

  val letters = ContextRoutes.of[Unit, IO] { case GET -> Root / "/b" as _ =>
    Ok("bee")
  }
  val shadow = ContextRoutes.of[Unit, IO] { case GET -> Root / "shadowed" as _ =>
    Ok("visible")
  }
  val root = ContextRoutes.of[Unit, IO] {
    case GET -> Root / "about" as _ =>
      Ok("about")
    case GET -> Root / "shadow" / "shadowed" as _ =>
      Ok("invisible")
  }

  val notFound = ContextRoutes.of[Unit, IO] { case _ as _ =>
    NotFound("Custom NotFound")
  }

  def middleware(routes: ContextRoutes[Unit, IO]): ContextRoutes[Unit, IO] =
    Kleisli((r: ContextRequest[IO, Unit]) =>
      if (r.req.uri.query.containsQueryParam("block")) OptionT.liftF(Ok(r.req.uri.path))
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
