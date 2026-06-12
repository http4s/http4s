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

import cats.data.Kleisli
import cats.data.OptionT
import cats.effect.IO
import org.http4s._
import org.http4s.metrics.TerminationType
import org.http4s.metrics.TerminationType.Canceled
import org.http4s.metrics.TestMetricsOps

final class MetricsSuite extends Http4sSuite {

  private val canceledRoutes: HttpRoutes[IO] =
    Kleisli((_: Request[IO]) => OptionT.liftF(IO.canceled.as(Response[IO]())))

  private val errorRoutes: HttpRoutes[IO] =
    Kleisli((_: Request[IO]) =>
      OptionT.liftF[IO, Response[IO]](IO.raiseError(new RuntimeException("boom")))
    )

  private def runToCompletion(routes: HttpRoutes[IO]): IO[Unit] =
    routes.run(Request[IO]()).value.start.flatMap(_.join).void

  test("canceled requests should be counted in recordTotalTime") {
    for {
      pair <- TestMetricsOps.create
      (ops, get) = pair
      mw = Metrics[IO](ops)(canceledRoutes)
      _ <- runToCompletion(mw)
      state <- get
    } yield {
      assertEquals(state.totalTime.size, 1)
      assertEquals(state.abnormal.map(_._2), List[TerminationType](Canceled))
      assertEquals(state.active, 0L)
    }
  }

  test("errored requests with errorResponseHandler returning None should be counted") {
    for {
      pair <- TestMetricsOps.create
      (ops, get) = pair
      mw = Metrics[IO](
        ops = ops,
        errorResponseHandler = (_: Throwable) => None,
      )(errorRoutes)
      _ <- mw.run(Request[IO]()).value.attempt
      state <- get
    } yield {
      assertEquals(state.totalTime.size, 1)
      assertEquals(state.abnormal.size, 1)
    }
  }

  test("errored requests with no response should not record in headersTime") {
    for {
      pair <- TestMetricsOps.create
      (ops, get) = pair
      mw = Metrics[IO](ops)(errorRoutes)
      _ <- mw.run(Request[IO]()).value.attempt
      state <- get
    } yield assertEquals(state.headersTime, Nil)
  }
}
