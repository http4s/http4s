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
import com.comcast.ip4s._
import fs2.Stream
import org.http4s._
import org.http4s.Request.Connection
import org.http4s.metrics.TerminationType
import org.http4s.metrics.TerminationType.Canceled
import org.http4s.metrics.TestMetricsOps
import org.http4s.metrics.TestMetricsOps2
import org.http4s.syntax.literals._
import org.typelevel.vault.Vault

final class MetricsSuite extends Http4sSuite {

  // Exercises the `(Canceled, None)` branch.
  private val canceledRoutes: HttpRoutes[IO] =
    Kleisli((_: Request[IO]) => OptionT.liftF(IO.canceled.as(Response[IO]())))

  // Exercises the `(Canceled, Some(status))` branch.
  private val canceledBodyRoutes: HttpRoutes[IO] =
    Kleisli((_: Request[IO]) =>
      OptionT.pure[IO](
        Response[IO](status = Status.Accepted).withBodyStream(
          Stream.emit(42.toByte) ++ Stream.eval(IO.canceled).drain
        )
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

  test("MetricsOps2 records an early error without inventing a response status") {
    val request =
      Request[IO](method = Method.POST, uri = Uri(path = path"/metrics"))

    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(errorRoutes).run(request).value.attempt
      state <- ops.state
    } yield {
      assertEquals(state.active, 0L)
      assertEquals(state.contexts, List(request.requestPrelude -> Some("POST")))
      assertEquals(state.increases, state.contexts)
      assertEquals(state.decreases, state.contexts)
      assertEquals(state.headers, Nil)
      assertEquals(state.totals.map(_.response), List(None))
      assert(state.totals.head.terminationType.exists(_.isInstanceOf[TerminationType.Error]))
      assertEquals(state.totals.map(_.context), List(Some("POST")))
      assertEquals(state.requestBodies.map(_.request), List(request.requestPrelude))
      assertEquals(state.requestBodies.map(_.context), List(Some("POST")))
      assertEquals(state.responseBodies, Nil)
    }
  }

  test("MetricsOps2 records cancellation before a response without a synthetic status") {
    for {
      ops <- TestMetricsOps2.create
      _ <- runToCompletion(Metrics[IO](ops)(canceledRoutes))
      state <- ops.state
    } yield {
      assertEquals(state.active, 0L)
      assertEquals(state.headers, Nil)
      assertEquals(state.totals.map(_.response), List(None))
      assertEquals(state.totals.map(_.terminationType), List(Some(Canceled)))
      assertEquals(state.responseBodies, Nil)
    }
  }

  test("MetricsOps2 records cancellation while streaming with the real response") {
    for {
      ops <- TestMetricsOps2.create
      _ <- runToCompletion(Metrics[IO](ops)(canceledBodyRoutes))
      state <- ops.state
    } yield {
      assertEquals(state.active, 0L)
      assertEquals(state.headers.size, 1)
      assertEquals(state.totals.flatMap(_.response.map(_.status)), List(Status.Accepted))
      assertEquals(state.totals.map(_.terminationType), List(Some(Canceled)))
      assertEquals(state.responseBodies.flatMap(_.response.map(_.status)), List(Status.Accepted))
      assertEquals(state.responseBodies.map(_.bodySizeBytes), List(1L))
    }
  }

  test("MetricsOps2 counts request and response body bytes") {
    val request = Request[IO](method = Method.PUT, uri = uri"/metrics")
      .withBodyStream(Stream.emits("request".getBytes).covary[IO])
    val routes = Kleisli((request: Request[IO]) =>
      OptionT.liftF(
        request.body.compile.drain.as(
          Response[IO](Status.Ok).withBodyStream(Stream.emits("response".getBytes).covary[IO])
        )
      )
    )

    for {
      ops <- TestMetricsOps2.create
      response <- Metrics[IO](ops)(routes).run(request).value
      _ <- response.traverse_(_.body.compile.drain)
      state <- ops.state
    } yield {
      assertEquals(state.requestBodies.map(_.bodySizeBytes), List(7L))
      assertEquals(state.responseBodies.map(_.bodySizeBytes), List(8L))
      assertEquals(state.contexts, List(request.requestPrelude -> Some("PUT")))
      assertEquals(state.increases, state.contexts)
      assertEquals(state.decreases, state.contexts)
    }
  }

  test("MetricsOps2 receives server connection information") {
    val connection = Connection(
      SocketAddress(ip"127.0.0.1", port"443"),
      SocketAddress(ip"192.0.2.1", port"12345"),
      secure = true,
    )
    val request = Request[IO](
      attributes = Vault.empty.insert(Request.Keys.ConnectionInfo, connection)
    )
    val routes = HttpRoutes.pure[IO](Response[IO](Status.NoContent))

    for {
      ops <- TestMetricsOps2.create
      response <- Metrics[IO](ops)(routes).run(request).value
      _ <- response.traverse_(_.body.compile.drain)
      state <- ops.state
    } yield assertEquals(state.connectionInfos, List(Some(connection)))
  }

  test("MetricsOps2 does not record a response size for an unmatched route") {
    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(HttpRoutes.empty[IO]).run(Request[IO]()).value
      state <- ops.state
    } yield {
      assertEquals(state.headers, Nil)
      assertEquals(state.totals.map(_.response), List(None))
      assertEquals(state.requestBodies.map(_.bodySizeBytes), List(0L))
      assertEquals(state.responseBodies, Nil)
    }
  }
}
