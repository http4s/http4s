/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.server.middleware

import cats.implicits._
import cats.effect._
import org.http4s._
import org.http4s.headers.{Date => HDate}
import org.http4s.syntax.all._

class DateSuite extends Http4sSuite {
  
  val service: HttpRoutes[IO] = HttpRoutes.of[IO] { case _ =>
    Response[IO](Status.Ok).pure[IO]
  }

  // Hack for https://github.com/typelevel/cats-effect/pull/682
  val testService = Date(service)
  val testApp = Date(service.orNotFound)

  val req = Request[IO]()

  test("always be very shortly before the current time httpRoutes") {
    val result = for {
      out <- testService(req).value
      now <- HttpDate.current[IO]
    } yield out.flatMap(_.headers.get(HDate)).map { case date =>
      val diff = now.epochSecond - date.date.epochSecond
      diff <= 2L
    }

    result.assertEquals(Some(true))
  }

  test("always be very shortly before the current time httpApp") {
    val result = for {
      out <- testApp(req)
      now <- HttpDate.current[IO]
    } yield out.headers.get(HDate).map { case date =>
      val diff = now.epochSecond - date.date.epochSecond
      diff <= 2L
    }

    result.assertEquals(Some(true))
  }

  test("not override a set date header") {
    val service = HttpRoutes
      .of[IO] { case _ =>
        Response[IO](Status.Ok)
          .putHeaders(HDate(HttpDate.Epoch))
          .pure[IO]
      }
      .orNotFound
    val test = Date(service)

    val result = for {
      out <- test(req)
      nowD <- HttpDate.current[IO]
    } yield out.headers.get(HDate).map { case date =>
      val now = nowD.epochSecond
      val diff = now - date.date.epochSecond
      now == diff
    }

    result.assertEquals(Some(true))
  }

  test("be created via httpRoutes constructor") {
    val httpRoute = Date.httpRoutes(service)

    httpRoute(req).value.map(_.flatMap(_.headers.get(HDate)).isDefined)
      .assertEquals(true)
  }

  test("be created via httpApp constructor") {
    val httpApp = Date.httpApp(service.orNotFound)

    httpApp(req).map(_.headers.get(HDate).isDefined)
      .assertEquals(true)
  }
}
