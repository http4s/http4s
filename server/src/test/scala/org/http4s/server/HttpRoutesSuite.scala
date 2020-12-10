/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server

import cats.effect._
import cats.syntax.all._
import org.http4s.Uri.uri
import org.http4s.syntax.all._

class HttpRoutesSuite extends Http4sSuite {
  val routes1 = HttpRoutes.of[IO] {
    case req if req.pathInfo == path"/match" =>
      Response[IO](Status.Ok).withEntity("match").pure[IO]

    case req if req.pathInfo == path"/conflict" =>
      Response[IO](Status.Ok).withEntity("routes1conflict").pure[IO]

    case req if req.pathInfo == path"/notfound" =>
      Response[IO](Status.NotFound).withEntity("notfound").pure[IO]
  }

  val routes2 = HttpRoutes.of[IO] {
    case req if req.pathInfo == path"/routes2" =>
      Response[IO](Status.Ok).withEntity("routes2").pure[IO]

    case req if req.pathInfo == path"/conflict" =>
      Response[IO](Status.Ok).withEntity("routes2conflict").pure[IO]
  }

  val aggregate1 = routes1 <+> routes2

  test("Return a valid Response from the first service of an aggregate") {
    aggregate1
      .orNotFound(Request[IO](uri = uri("/match")))
      .flatMap(_.as[String])
      .assertEquals("match")
  }

  test("Return a custom NotFound from the first service of an aggregate") {
    aggregate1
      .orNotFound(Request[IO](uri = uri("/notfound")))
      .flatMap(_.as[String])
      .assertEquals("notfound")
  }

  test("Accept the first matching route in the case of overlapping paths") {
    aggregate1
      .orNotFound(Request[IO](uri = uri("/conflict")))
      .flatMap(_.as[String])
      .assertEquals("routes1conflict")
  }

  test("Fall through the first service that doesn't match to a second matching service") {
    aggregate1
      .orNotFound(Request[IO](uri = uri("/routes2")))
      .flatMap(_.as[String])
      .assertEquals("routes2")
  }

  test("Properly fall through two aggregated service if no path matches") {
    aggregate1
      .apply(Request[IO](uri = uri("/wontMatch")))
      .value
      .map(_ == Option.empty[Response[IO]])
      .assertEquals(true)
  }
}
