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
import cats.effect.Resource
import fs2.Stream
import org.http4s._
import org.http4s.client.Client
import org.http4s.metrics.TerminationType
import org.http4s.metrics.TerminationType.Canceled
import org.http4s.metrics.TestMetricsOps
import org.http4s.metrics.TestMetricsOps2
import org.http4s.syntax.all._

final class MetricsSuite extends Http4sSuite {

  private val req: Request[IO] = Request[IO](uri = uri"/x")

  // Hangs before producing a response, so cancellation lands with no status in hand.
  private def hangingClient(ready: Deferred[IO, Unit]): Client[IO] =
    Client[IO]((_: Request[IO]) => Resource.eval(ready.complete(()) >> IO.never[Response[IO]]))

  // Hangs partway through the body, so cancellation lands with a status already in hand.
  private def hangingBodyClient(ready: Deferred[IO, Unit]): Client[IO] =
    Client[IO]((_: Request[IO]) =>
      Resource.pure[IO, Response[IO]](
        Response[IO](status = Status.Accepted)
          .withBodyStream(Stream.eval(ready.complete(()) >> IO.never[Byte]))
      )
    )

  // Runs `use` against a metered client, cancels it once the client signals, and returns what the middleware recorded.
  private def cancelDuring(
      mkClient: Deferred[IO, Unit] => Client[IO]
  )(use: Response[IO] => IO[Unit]): IO[TestMetricsOps.State] =
    for {
      ready <- Deferred[IO, Unit]
      ops <- TestMetricsOps.create
      fiber <- Metrics[IO](ops)(mkClient(ready)).run(req).use(use).start
      _ <- ready.get
      _ <- fiber.cancel
      _ <- fiber.join
      state <- ops.state
    } yield state

  test("a request canceled before a response records the termination but no total time") {
    cancelDuring(hangingClient)(_ => IO.unit).map { state =>
      // A synthetic status here would be indistinguishable from one an origin really returned, so
      // the cancellation is visible only as an abnormal termination.
      assertEquals(state.terminationTypes, List[TerminationType](Canceled))
      assertEquals(state.totalTime, Nil)
      assertEquals(state.headersTime, Nil)
      assertEquals(state.active, 0L)
    }
  }

  test("a request canceled while streaming the body is counted with the real status") {
    cancelDuring(hangingBodyClient)(_.body.compile.drain).map { state =>
      assertEquals(state.terminationTypes, List[TerminationType](Canceled))
      assertEquals(state.statuses, List(Status.Accepted))
      assertEquals(state.headersTime.size, 1)
      assertEquals(state.active, 0L)
    }
  }

  test("MetricsOps2 receives request and response preludes") {
    val request = Request[IO](method = Method.POST, uri = uri"/metrics").withEntity("request")
    val client =
      Client[IO]((_: Request[IO]) => Resource.pure(Response[IO](Status.Created).withEntity("ok")))

    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(client).run(request).use(_.body.compile.drain)
      state <- ops.state
    } yield {
      assertEquals(state.active, 0L)
      assertEquals(state.headers.map(_._1), List(request.requestPrelude))
      assertEquals(state.headers.map(_._3), List(Some("POST")))
      assertEquals(state.totals.map(_.status), List(Some(Status.Created)))
      assertEquals(state.totals.map(_.terminationType), List(None))
      assertEquals(state.requestBodies, List(request.requestPrelude))
      assertEquals(state.responseBodies.map(_.status), List(Status.Created))
    }
  }

  test("MetricsOps2 records cancellation before a response without inventing a status") {
    for {
      ready <- Deferred[IO, Unit]
      ops <- TestMetricsOps2.create
      fiber <- Metrics[IO](ops)(hangingClient(ready)).run(req).use_.start
      _ <- ready.get
      _ <- fiber.cancel
      _ <- fiber.join
      state <- ops.state
    } yield {
      assertEquals(state.active, 0L)
      assertEquals(state.headers, Nil)
      assertEquals(state.totals.map(_.status), List(None))
      assertEquals(state.totals.map(_.terminationType), List(Some(Canceled)))
      assertEquals(state.requestBodies, List(req.requestPrelude))
      assertEquals(state.responseBodies, Nil)
    }
  }
}
