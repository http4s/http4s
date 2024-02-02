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

package org.http4s.client.middleware

import cats.Applicative
import cats.effect.Clock
import cats.effect.IO
import cats.effect.Ref
import org.http4s.Http4sSuite
import org.http4s.HttpDate
import org.http4s.HttpRoutes
import org.http4s.Request
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.headers.Date
import org.http4s.syntax.all.http4sLiteralsSyntax

import java.time.Duration
import java.time.Instant
import scala.concurrent.duration.FiniteDuration

class HistorySuite extends Http4sSuite {

  private val app = HttpRoutes
    .of[IO] { case _ =>
      Ok()
    }
    .orNotFound

  private val defaultClient = Client.fromHttpApp(app)

  private val date1 = Date(HttpDate.unsafeFromInstant(Instant.now()))
  private val date2 = Date(HttpDate.unsafeFromInstant(Instant.now().plus(Duration.ofMinutes(1L))))
  private val date3 = Date(HttpDate.unsafeFromInstant(Instant.now().plus(Duration.ofMinutes(2L))))

  private val req1 = Request[IO](uri = uri"/request").putHeaders(date1)
  private val req2 = Request[IO](uri = uri"/request").putHeaders(date2)
  private val req3 = Request[IO](uri = uri"/request").putHeaders(date3)

  test("History middeware should return empty history if no sites have been visited") {
    val expected: Vector[HistoryEntry] = Vector.empty

    Ref.of[IO, Vector[HistoryEntry]](Vector.empty).flatMap { ref =>
      HistoryBuilder.default[IO](defaultClient, ref).withMaxSize(3).build
      ref.get.assertEquals(expected)
    }
  }

  test("History middeware should return 1 history item if 1 site has been visited") {
    val expected = Vector(HistoryEntry(req1.headers.get[Date].get.date, req1.method, req1.uri))

    Ref.of[IO, Vector[HistoryEntry]](Vector.empty).flatMap { ref =>
      HistoryBuilder
        .default[IO](defaultClient, ref)
        .withMaxSize(3)
        .build
        .run(req1)
        .use(_ => IO.unit) >> ref.get
        .assertEquals(expected)
    }
  }

  test("History middeware should return visits in order of most recent to oldest") {
    val expected: Vector[HistoryEntry] =
      Vector(
        HistoryEntry(req3.headers.get[Date].get.date, req3.method, req3.uri),
        HistoryEntry(req2.headers.get[Date].get.date, req2.method, req2.uri),
        HistoryEntry(req1.headers.get[Date].get.date, req1.method, req1.uri),
      )

    Ref.of[IO, Vector[HistoryEntry]](Vector.empty).flatMap { ref =>
      HistoryBuilder.default[IO](defaultClient, ref).withMaxSize(3).build.run(req1).use_ >>
        HistoryBuilder.default[IO](defaultClient, ref).withMaxSize(3).build.run(req2).use_ >>
        HistoryBuilder.default[IO](defaultClient, ref).withMaxSize(3).build.run(req3).use_ >>
        ref.get.assertEquals(expected)
    }
  }

  test("History middeware should return max number of visits if visits exceeds maxSize") {

    val expected: Vector[HistoryEntry] = Vector(
      HistoryEntry(req3.headers.get[Date].get.date, req3.method, req3.uri),
      HistoryEntry(req2.headers.get[Date].get.date, req2.method, req2.uri),
    )

    Ref.of[IO, Vector[HistoryEntry]](Vector.empty).flatMap { ref =>
      val historyClient = HistoryBuilder.default[IO](defaultClient, ref).withMaxSize(2).build

      historyClient.run(req1).use_ >>
        historyClient.run(req2).use_ >>
        historyClient.run(req3).use_ >>
        ref.get.assertEquals(expected)
    }
  }

  test("History middeware should allow and use Clock parameter for httpDate timestamp") {

    implicit val testClock: Clock[IO] = new Clock[IO] {
      override def applicative: Applicative[IO] = Applicative[IO] // IO.asyncForIO

      override def monotonic: IO[FiniteDuration] =
        IO.pure(FiniteDuration(0L, scala.concurrent.duration.HOURS))

      override def realTime: IO[FiniteDuration] =
        IO.pure(FiniteDuration(0L, scala.concurrent.duration.HOURS))
    }

    val expected: Vector[HistoryEntry] = Vector(
      HistoryEntry(req3.headers.get[Date].get.date, req3.method, req3.uri),
      HistoryEntry(req2.headers.get[Date].get.date, req2.method, req2.uri),
    )

    implicitly[Clock[IO]]
    Ref.of[IO, Vector[HistoryEntry]](Vector.empty).flatMap { ref =>
      val historyClient = HistoryBuilder.default(defaultClient, ref).withMaxSize(2).build

      historyClient.run(req1).use_ >>
        historyClient.run(req2).use_ >>
        historyClient.run(req3).use_ >>
        ref.get.assertEquals(expected)
    }
  }

}
