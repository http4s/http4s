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

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect._
import cats.syntax.all._
import org.http4s.dsl.io._
import org.http4s.syntax.all._

import scala.util.Try

class ContextRouterSuite extends Http4sSuite {
  private val numbers = ContextRoutes.of[Unit, IO] { case GET -> Root / "1" as _ =>
    Ok("one")
  }
  private val numbers2 = ContextRoutes.of[Unit, IO] { case GET -> Root / "1" as _ =>
    Ok("two")
  }

  private val letters = ContextRoutes.of[Unit, IO] { case GET -> Root / "/b" as _ =>
    Ok("bee")
  }
  private val shadow = ContextRoutes.of[Unit, IO] { case GET -> Root / "shadowed" as _ =>
    Ok("visible")
  }
  private val root = ContextRoutes.of[Unit, IO] {
    case GET -> Root / "about" as _ =>
      Ok("about")
    case GET -> Root / "shadow" / "shadowed" as _ =>
      Ok("invisible")
  }

  private val numElem =
    ContextRouter.Segment[Unit]((_, s) => OptionT.fromOption[IO](Try(s.decoded().toInt).toOption))
  private val element = ContextRouter.Segment[Unit]((_, s) => OptionT.pure[IO](s.decoded()))
  private val routable = ContextRouter.of(
    "/" -> ContextRoutes.of[Unit, IO] { case GET -> Root as _ => Ok("static") },
    "/2" -> ContextRouter.of[IO, Unit](
      element -> ContextRoutes.of { case GET -> Root as x => Ok(x) }
    ),
    numElem -> ContextRoutes.of { case GET -> Root as x => Ok(s"${x * 2}") },
    element -> ContextRoutes.of { case GET -> Root as x =>
      Ok(x)
    },
  )

  private val notFound = ContextRoutes.of[Unit, IO] { case _ as _ =>
    NotFound("Custom NotFound")
  }

  def middleware(routes: ContextRoutes[Unit, IO]): ContextRoutes[Unit, IO] =
    Kleisli((r: ContextRequest[IO, Unit]) =>
      if (r.req.uri.query.containsQueryParam("block"))
        OptionT.liftF(Ok(r.req.uri.path.renderString))
      else routes(r)
    )

  private val service = ContextRouter[IO, Unit](
    "/numbers" -> numbers,
    "/numb" -> middleware(numbers2),
    "/" -> root,
    "/shadow" -> shadow,
    "/letters" -> letters,
    "/routable" -> routable,
  )

  test("routable") {
    service
      .orNotFound(ContextRequest((), Request[IO](GET, uri"/routable")))
      .flatMap(_.as[String])
      .assertEquals("static") *>
      service
        .orNotFound(ContextRequest((), Request[IO](GET, uri"/routable/2")))
        .flatMap(_.as[String])
        .assertEquals("4") *>
      service
        .orNotFound(ContextRequest((), Request[IO](GET, uri"/routable/2/3")))
        .flatMap(_.as[String])
        .assertEquals("3") *>
      service
        .orNotFound(ContextRequest((), Request[IO](GET, uri"/routable/foo")))
        .flatMap(_.as[String])
        .assertEquals("foo")
  }

  test("translate mount prefixes") {
    service
      .orNotFound(ContextRequest((), Request[IO](GET, uri"/numbers/1")))
      .flatMap(_.as[String])
      .assertEquals("one") *>
      service
        .orNotFound(ContextRequest((), Request[IO](GET, uri"/numb/1")))
        .flatMap(_.as[String])
        .assertEquals("two") *>
      service
        .orNotFound(ContextRequest((), Request[IO](GET, uri"/numbe?block")))
        .map(_.status)
        .assertEquals(NotFound)
  }

  test("require the correct prefix") {
    service
      .orNotFound(ContextRequest((), Request[IO](GET, uri"/letters/1")))
      .flatMap { resp =>
        resp.as[String].map { b =>
          b =!= "bee" && b =!= "one" && resp.status === NotFound
        }
      }
      .assert
  }

  test("support root mappings") {
    service
      .orNotFound(ContextRequest((), Request[IO](GET, uri"/about")))
      .flatMap(_.as[String])
      .assertEquals("about")
  }

  test("match longer prefixes first") {
    service
      .orNotFound(ContextRequest((), Request[IO](GET, uri"/shadow/shadowed")))
      .flatMap(_.as[String])
      .assertEquals("visible")
  }

  test("404 on unknown prefixes") {
    service
      .orNotFound(ContextRequest((), Request[IO](GET, uri"/symbols/~")))
      .map(_.status)
      .assertEquals(NotFound)
  }

  test("Allow passing through of routes with identical prefixes") {
    ContextRouter[IO, Unit]("" -> letters, "" -> numbers)
      .orNotFound(ContextRequest((), Request[IO](GET, uri"/1")))
      .flatMap(_.as[String])
      .assertEquals("one")
  }

  test("Serve custom NotFound responses") {
    ContextRouter[IO, Unit]("/foo" -> notFound)
      .orNotFound(ContextRequest((), Request[IO](uri = uri"/foo/bar")))
      .flatMap(_.as[String])
      .assertEquals("Custom NotFound")
  }

  test("Return the fallthrough response if no route is found") {
    val router = ContextRouter[IO, Unit]("/foo" -> notFound)
    router(ContextRequest((), Request[IO](uri = uri"/bar"))).value
      .map(_ == Option.empty[Response[IO]])
      .assert
  }
}
