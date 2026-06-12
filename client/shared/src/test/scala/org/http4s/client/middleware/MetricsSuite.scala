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

import cats.effect.Deferred
import cats.effect.IO
import org.http4s._
import org.http4s.client.Client
import org.http4s.metrics.TerminationType
import org.http4s.metrics.TerminationType.Canceled
import org.http4s.metrics.TestMetricsOps
import org.http4s.syntax.all._

final class MetricsSuite extends Http4sSuite {

  private def hangingClient(signal: Deferred[IO, Unit]): Client[IO] =
    Client.fromHttpApp[IO](
      HttpApp[IO]((_: Request[IO]) => signal.complete(()) >> IO.never)
    )

  private val req: Request[IO] = Request[IO](uri = uri"/x")

  test("canceled requests record abnormal termination Canceled") {
    Deferred[IO, Unit].flatMap { ready =>
      for {
        pair <- TestMetricsOps.create
        (ops, get) = pair
        metered = Metrics[IO](ops)(hangingClient(ready))
        fiber <- metered.run(req).use_.start
        _ <- ready.get
        _ <- fiber.cancel
        _ <- fiber.join
        state <- get
      } yield {
        assertEquals(state.active, 0L)
        assertEquals(state.abnormal.map(_._2), List[TerminationType](Canceled))
      }
    }
  }

  test("canceled requests should be counted in recordTotalTime") {
    Deferred[IO, Unit].flatMap { ready =>
      for {
        pair <- TestMetricsOps.create
        (ops, get) = pair
        metered = Metrics[IO](ops)(hangingClient(ready))
        fiber <- metered.run(req).use_.start
        _ <- ready.get
        _ <- fiber.cancel
        _ <- fiber.join
        state <- get
      } yield assertEquals(state.totalTime.size, 1)
    }
  }
}
