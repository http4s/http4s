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

import cats.syntax.all._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import org.http4s.dsl.io._
import org.http4s.syntax.all._

class RouterSuite extends Http4sSuite {
  val numbers = HttpRoutes.of[IO] { case Get -> Root / "1" =>
    Ok("one")
  }
  val numbers2 = HttpRoutes.of[IO] { case Get -> Root / "1" =>
    Ok("two")
  }

  val letters = HttpRoutes.of[IO] { case Get -> Root / "/b" =>
    Ok("bee")
  }
  val shadow = HttpRoutes.of[IO] { case Get -> Root / "shadowed" =>
    Ok("visible")
  }
  val root = HttpRoutes.of[IO] {
    case Get -> Root / "about" =>
      Ok("about")
    case Get -> Root / "shadow" / "shadowed" =>
      Ok("invisible")
  }

  val notFound = HttpRoutes.of[IO] { case _ =>
    NotFound("Custom NotFound")
  }

  def middleware(routes: HttpRoutes[IO]): HttpRoutes[IO] =
    Kleisli((r: Request[IO]) =>
      if (r.uri.query.containsQueryParam("block")) OptionT.liftF(Ok(r.uri.path.renderString))
      else routes(r))

  val service = Router[IO](
    "/numbers" -> numbers,
    "/numb" -> middleware(numbers2),
    "/" -> root,
    "/shadow" -> shadow,
    "/letters" -> letters
  )

  test("translate mount prefixes") {
    service
      .orNotFound(Request[IO](Get, uri"/numbers/1"))
      .flatMap(_.as[String])
      .assertEquals("one") *>
      service
        .orNotFound(Request[IO](Get, uri"/numb/1"))
        .flatMap(_.as[String])
        .assertEquals("two") *>
      service.orNotFound(Request[IO](Get, uri"/numbe?block")).map(_.status).assertEquals(NotFound)
  }

  test("require the correct prefix") {
    service
      .orNotFound(Request[IO](Get, uri"/letters/1"))
      .flatMap { res =>
        res.as[String].map { b =>
          b =!= "bee" && b =!= "one" && res.status === NotFound
        }
      }
      .assert
  }

  test("support root mappings") {
    service.orNotFound(Request[IO](Get, uri"/about")).flatMap(_.as[String]).assertEquals("about")
  }

  test("match longer prefixes first") {
    service
      .orNotFound(Request[IO](Get, uri"/shadow/shadowed"))
      .flatMap(_.as[String])
      .assertEquals("visible")
  }

  test("404 on unknown prefixes") {
    service.orNotFound(Request[IO](Get, uri"/symbols/~")).map(_.status).assertEquals(NotFound)
  }

  test("Allow passing through of routes with identical prefixes") {
    Router[IO]("" -> letters, "" -> numbers)
      .orNotFound(Request[IO](Get, uri"/1"))
      .flatMap(_.as[String])
      .assertEquals("one")
  }

  test("Serve custom NotFound responses") {
    Router[IO]("/foo" -> notFound)
      .orNotFound(Request[IO](uri = uri"/foo/bar"))
      .flatMap {
        _.as[String]
      }
      .assertEquals("Custom NotFound")
  }

  test("Return the fallthrough response if no route is found") {
    val router = Router[IO]("/foo" -> notFound)
    router(Request[IO](uri = uri"/bar")).value
      .map(_ == Option.empty[Response[IO]])
      .assert
  }
}
