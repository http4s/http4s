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

package org.http4s.server.middleware

import cats.effect._
import cats.implicits._
import org.http4s._
import org.http4s.headers.{Date => HDate}
import org.http4s.syntax.all._

class DateSuite extends Http4sSuite {

  val service: HttpRoutes[IO] = HttpRoutes.of[IO] { case _ =>
    Response[IO](Status.Ok).pure[IO]
  }

  // Hack for https://github.com/typelevel/cats-effect/pull/682
  private val testService = Date(service)
  private val testApp = Date(service.orNotFound)

  private val req = Request[IO]()

  test("Date should always be very shortly before the current time httpRoutes") {
    (for {
      out <- testService(req).value
      now <- HttpDate.current[IO]
    } yield out.flatMap(_.headers.get[HDate]).exists { case (date: HDate) =>
      val diff = now.epochSecond - date.date.epochSecond
      diff <= 2L
    }).assertEquals(true)
  }

  test("Date should always be very shortly before the current time httpApp") {
    (for {
      out <- testApp(req)
      now <- HttpDate.current[IO]
    } yield out.headers.get[HDate].exists { date =>
      val diff = now.epochSecond - date.date.epochSecond
      diff <= 2L
    }).assertEquals(true)
  }

  test("Date should not override a set date header") {
    val service = HttpRoutes
      .of[IO] { case _ =>
        Response[IO](Status.Ok)
          .putHeaders(HDate(HttpDate.Epoch))
          .pure[IO]
      }
      .orNotFound
    val test = Date(service)

    (for {
      out <- test(req)
      nowD <- HttpDate.current[IO]
    } yield out.headers.get[HDate].exists { date =>
      val now = nowD.epochSecond
      val diff = now - date.date.epochSecond
      now == diff
    }).assertEquals(true)
  }

  test("Date should be created via httpRoutes constructor") {
    val httpRoute = Date.httpRoutes(service)

    val r = for {
      response <- httpRoute(req).value
    } yield response.flatMap(_.headers.get[HDate]).isDefined
    assertIOBoolean(r)
  }

  test("Date should be created via httpApp constructor") {
    val httpApp = Date.httpApp(service.orNotFound)

    val r = for {
      response <- httpApp(req)
    } yield response.headers.get[HDate].isDefined
    assertIOBoolean(r)
  }
}
