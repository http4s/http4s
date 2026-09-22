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
import cats.syntax.all._
import fs2.Stream
import org.http4s._
import org.http4s.metrics.TerminationType
import org.http4s.metrics.TerminationType.Canceled
import org.http4s.metrics.TestMetricsOps

final class MetricsSuite extends Http4sSuite {

  // Exercises the `(Canceled, None)` branch.
  private val canceledRoutes: HttpRoutes[IO] =
    Kleisli((_: Request[IO]) => OptionT.liftF(IO.canceled.as(Response[IO]())))

  // Exercises the `(Canceled, Some(status))` branch.
  private val canceledBodyRoutes: HttpRoutes[IO] =
    Kleisli((_: Request[IO]) =>
      OptionT.pure[IO](
        Response[IO](status = Status.Accepted).withBodyStream(Stream.eval(IO.canceled).drain)
      )
    )

  private val errorRoutes: HttpRoutes[IO] =
    Kleisli((_: Request[IO]) =>
      OptionT.liftF[IO, Response[IO]](IO.raiseError(new RuntimeException("boom")))
    )

  // Bookkeeping only completes once the response body terminates, hence the drain.
  private def runToCompletion(routes: HttpRoutes[IO]): IO[Unit] =
    routes
      .run(Request[IO]())
      .value
      .flatMap(_.traverse_(_.body.compile.drain))
      .start
      .flatMap(_.join)
      .void

  test("a request canceled before a response is counted as CanceledStatus") {
    for {
      ops <- TestMetricsOps.create
      _ <- runToCompletion(Metrics[IO](ops)(canceledRoutes))
      state <- ops.state
    } yield {
      assertEquals(state.statuses, List(Metrics.CanceledStatus))
      assertEquals(state.terminationTypes, List[TerminationType](Canceled))
      assertEquals(state.headersTime, Nil)
      assertEquals(state.active, 0L)
    }
  }

  test("a request canceled while streaming the body is counted with the real status") {
    for {
      ops <- TestMetricsOps.create
      _ <- runToCompletion(Metrics[IO](ops)(canceledBodyRoutes))
      state <- ops.state
    } yield {
      assertEquals(state.statuses, List(Status.Accepted))
      assertEquals(state.terminationTypes, List[TerminationType](Canceled))
      assertEquals(state.headersTime.size, 1)
      assertEquals(state.active, 0L)
    }
  }

  test("an errored request is counted using the default errorResponseHandler") {
    for {
      ops <- TestMetricsOps.create
      _ <- Metrics[IO](ops)(errorRoutes).run(Request[IO]()).value.attempt
      state <- ops.state
    } yield {
      assertEquals(state.statuses, List(Status.InternalServerError))
      assertEquals(state.abnormal.size, 1)
    }
  }

  test("an errorResponseHandler returning None excludes the request from recordTotalTime") {
    for {
      ops <- TestMetricsOps.create
      mw = Metrics[IO](ops = ops, errorResponseHandler = (_: Throwable) => None)(errorRoutes)
      _ <- mw.run(Request[IO]()).value.attempt
      state <- ops.state
    } yield {
      // `None` is a documented opt-out from the counter; the termination is still recorded.
      assertEquals(state.totalTime, Nil)
      assertEquals(state.abnormal.size, 1)
    }
  }

  test("an error before a response records no headers time") {
    for {
      ops <- TestMetricsOps.create
      _ <- Metrics[IO](ops)(errorRoutes).run(Request[IO]()).value.attempt
      state <- ops.state
    } yield assertEquals(state.headersTime, Nil)
  }
}
