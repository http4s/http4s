/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server

import cats.syntax.all._
import cats.data.{Kleisli, OptionT}
import cats.effect._
import org.http4s.dsl.io._
import org.http4s.syntax.all._

class ContextRouterSuite extends Http4sSuite {
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
      .assertEquals(true)
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
      .assertEquals(true)
  }
}
