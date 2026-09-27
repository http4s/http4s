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
import cats.effect.Deferred
import cats.effect.IO
import cats.effect.Ref
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Stream
import org.http4s.Request.Connection
import org.http4s._
import org.http4s.headers.`Content-Length`
import org.http4s.metrics.MetricsOps2
import org.http4s.metrics.MetricsRequest
import org.http4s.metrics.TerminationType
import org.http4s.metrics.TerminationType.Canceled
import org.http4s.metrics.TestMetricsOps
import org.http4s.metrics.TestMetricsOps2
import org.http4s.syntax.literals._
import org.typelevel.vault.Vault

import scala.concurrent.duration.FiniteDuration

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

  private def runAppToCompletion(app: HttpApp[IO]): IO[Unit] =
    app
      .run(Request[IO]())
      .flatMap(_.body.compile.drain)
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
      _ <- Metrics[IO](ops)(errorRoutes.orNotFound).run(request).attempt
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
      _ <- runAppToCompletion(Metrics[IO](ops)(canceledRoutes.orNotFound))
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
      _ <- runAppToCompletion(Metrics[IO](ops)(canceledBodyRoutes.orNotFound))
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
      response <- Metrics[IO](ops)(routes.orNotFound).run(request)
      _ <- response.body.compile.drain
      state <- ops.state
    } yield {
      assertEquals(state.requestBodies.map(_.bodySizeBytes), List(7L))
      assertEquals(state.responseBodies.map(_.bodySizeBytes), List(8L))
      assertEquals(state.contexts, List(request.requestPrelude -> Some("PUT")))
      assertEquals(state.increases, state.contexts)
      assertEquals(state.decreases, state.contexts)
    }
  }

  test("MetricsOps2 uses Content-Length after the body stream completes") {
    val request = Request[IO](method = Method.PUT, uri = uri"/metrics")
      .withBodyStream(Stream.emits("request".getBytes).covary[IO])
      .putHeaders(`Content-Length`.unsafeFromLong(70L))
    val routes = Kleisli((request: Request[IO]) =>
      OptionT.liftF(
        request.body.compile.drain.as(
          Response[IO](Status.Ok)
            .withBodyStream(Stream.emits("response".getBytes).covary[IO])
            .putHeaders(`Content-Length`.unsafeFromLong(80L))
        )
      )
    )

    for {
      ops <- TestMetricsOps2.create
      response <- Metrics[IO](ops)(routes.orNotFound).run(request)
      _ <- response.body.compile.drain
      state <- ops.state
    } yield {
      assertEquals(state.requestBodies.map(_.bodySizeBytes), List(70L))
      assertEquals(state.responseBodies.map(_.bodySizeBytes), List(80L))
    }
  }

  test("MetricsOps2 omits a declared-length request body that is not evaluated") {
    val request = Request[IO](method = Method.POST)
      .withBodyStream(Stream.emits("request".getBytes).covary[IO])
      .putHeaders(`Content-Length`.unsafeFromLong(70L))

    for {
      ops <- TestMetricsOps2.create
      response <- Metrics[IO](ops)(HttpApp.pure[IO](Response[IO](Status.NoContent))).run(request)
      _ <- response.body.compile.drain
      state <- ops.state
    } yield assertEquals(state.requestBodies, Nil)
  }

  test("MetricsOps2 omits a declared-length response body that fails") {
    val failure = new RuntimeException("body failed")
    val app = HttpApp.pure[IO](
      Response[IO](Status.Ok)
        .withBodyStream(
          Stream.emits("response".getBytes).covary[IO] ++ Stream.raiseError[IO](failure)
        )
        .putHeaders(`Content-Length`.unsafeFromLong(80L))
    )

    for {
      ops <- TestMetricsOps2.create
      response <- Metrics[IO](ops)(app).run(Request[IO]())
      _ <- response.body.compile.drain.attempt
      state <- ops.state
    } yield {
      assertEquals(state.responseBodies, Nil)
      assert(state.totals.head.terminationType.exists(_.isInstanceOf[TerminationType.Abnormal]))
    }
  }

  test("MetricsOps2 records partial bytes when an undeclared response body fails") {
    val failure = new RuntimeException("body failed")
    val app = HttpApp.pure[IO](
      Response[IO](Status.Ok).withBodyStream(
        Stream.emits("response".getBytes).covary[IO] ++ Stream.raiseError[IO](failure)
      )
    )

    for {
      ops <- TestMetricsOps2.create
      response <- Metrics[IO](ops)(app).run(Request[IO]())
      _ <- response.body.compile.drain.attempt
      state <- ops.state
    } yield {
      assertEquals(state.responseBodies.map(_.bodySizeBytes), List(8L))
      assert(
        state.responseBodies.head.terminationType.exists(
          _.isInstanceOf[TerminationType.Abnormal]
        )
      )
    }
  }

  test("MetricsOps2 treats a HEAD response body as empty despite Content-Length") {
    val request = Request[IO](method = Method.HEAD, uri = uri"/metrics")
    val routes = HttpRoutes.pure[IO](
      Response[IO](Status.Ok).putHeaders(`Content-Length`.unsafeFromLong(80L))
    )

    for {
      ops <- TestMetricsOps2.create
      response <- Metrics[IO](ops)(routes.orNotFound).run(request)
      _ <- response.body.compile.drain
      state <- ops.state
    } yield assertEquals(state.responseBodies.map(_.bodySizeBytes), List(0L))
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
      response <- Metrics[IO](ops)(routes.orNotFound).run(request)
      _ <- response.body.compile.drain
      state <- ops.state
    } yield assertEquals(state.connectionInfos, List(Some(connection)))
  }

  test("MetricsOps2 records the actual fallback response from an HttpApp") {
    for {
      ops <- TestMetricsOps2.create
      response <- Metrics[IO](ops)(HttpRoutes.empty[IO].orNotFound).run(Request[IO]())
      _ <- response.body.compile.drain
      state <- ops.state
    } yield {
      assertEquals(state.headers.size, 1)
      assertEquals(state.totals.flatMap(_.response.map(_.status)), List(Status.NotFound))
      assertEquals(state.requestBodies.map(_.bodySizeBytes), List(0L))
      assertEquals(state.responseBodies.map(_.bodySizeBytes), List(9L))
    }
  }

  test("MetricsOps2 bypasses instrumentation when createContext returns None") {
    val app = Kleisli((request: Request[IO]) =>
      request.body.compile.drain.as(Response[IO](Status.Accepted))
    )
    val request = Request[IO]().withEntity("request")

    for {
      ops <- TestMetricsOps2.create(_ => false)
      response <- Metrics[IO](ops)(app).run(request)
      _ <- response.body.compile.drain
      state <- ops.state
    } yield {
      assertEquals(response.status, Status.Accepted)
      assertEquals(state, TestMetricsOps2.State.empty)
    }
  }

  test("MetricsOps2 records terminal metrics once when a response body is replayed") {
    val app = HttpApp.pure[IO](
      Response[IO](Status.Ok).withBodyStream(Stream.emit(0.toByte).covary[IO])
    )

    for {
      ops <- TestMetricsOps2.create
      response <- Metrics[IO](ops)(app).run(Request[IO]())
      _ <- response.body.compile.drain
      _ <- response.body.compile.drain
      state <- ops.state
    } yield {
      assertEquals(state.active, 0L)
      assertEquals(state.decreases.size, 1)
      assertEquals(state.totals.size, 1)
      assertEquals(state.requestBodies.size, 1)
      assertEquals(state.responseBodies.size, 1)
    }
  }

  test("MetricsOps2 records terminal metrics once when a response body is replayed concurrently") {
    for {
      entered <- Ref.of[IO, Int](0)
      bothEntered <- Deferred[IO, Unit]
      body = Stream.eval(
        entered
          .updateAndGet(_ + 1)
          .flatMap(count => bothEntered.complete(()).void.whenA(count == 2)) >> bothEntered.get
      ) >> Stream.emit(0.toByte)
      app = HttpApp.pure[IO](Response[IO](Status.Ok).withBodyStream(body))
      ops <- TestMetricsOps2.create
      response <- Metrics[IO](ops)(app).run(Request[IO]())
      _ <- List.fill(2)(response.body.compile.drain).parSequence_
      state <- ops.state
    } yield {
      assertEquals(state.active, 0L)
      assertEquals(state.decreases.size, 1)
      assertEquals(state.totals.size, 1)
      assertEquals(state.requestBodies.size, 1)
      assertEquals(state.responseBodies.size, 1)
    }
  }

  test("MetricsOps2 does not finish again after the first response body evaluation is canceled") {
    for {
      evaluations <- Ref.of[IO, Int](0)
      firstStarted <- Deferred[IO, Unit]
      body = Stream.eval(evaluations.getAndUpdate(_ + 1)).flatMap {
        case 0 => Stream.eval(firstStarted.complete(())) >> Stream.never[IO]
        case _ => Stream.emit(0.toByte)
      }
      app = HttpApp.pure[IO](Response[IO](Status.Ok).withBodyStream(body))
      ops <- TestMetricsOps2.create
      response <- Metrics[IO](ops)(app).run(Request[IO]())
      first <- response.body.compile.drain.start
      _ <- firstStarted.get
      _ <- first.cancel
      _ <- first.join
      _ <- response.body.compile.drain
      state <- ops.state
    } yield {
      assertEquals(state.active, 0L)
      assertEquals(state.decreases.size, 1)
      assertEquals(state.totals.map(_.terminationType), List(Some(Canceled)))
      assertEquals(state.requestBodies.size, 1)
      assertEquals(state.responseBodies.size, 1)
    }
  }

  test("MetricsOps2 keeps completion claimed when a terminal callback fails") {
    val failure = new RuntimeException("record total failed")

    for {
      active <- Ref.of[IO, Long](0L)
      totalAttempts <- Ref.of[IO, Int](0)
      ops = new MetricsOps2[IO] {
        type Context = Unit

        def createContext(request: MetricsRequest): IO[Option[Context]] = IO.pure(Some(()))

        def increaseActiveRequests(request: MetricsRequest, context: Context): IO[Unit] =
          active.update(_ + 1L)

        def decreaseActiveRequests(request: MetricsRequest, context: Context): IO[Unit] =
          active.update(_ - 1L)

        def recordHeadersTime(
            request: MetricsRequest,
            elapsed: FiniteDuration,
            context: Context,
        ): IO[Unit] = IO.unit

        def recordTotalTime(
            request: MetricsRequest,
            response: Option[ResponsePrelude],
            terminationType: Option[TerminationType],
            elapsed: FiniteDuration,
            context: Context,
        ): IO[Unit] = totalAttempts.update(_ + 1) >> IO.raiseError(failure)

        def recordRequestBodySize(
            request: MetricsRequest,
            response: Option[ResponsePrelude],
            terminationType: Option[TerminationType],
            bodySizeBytes: Long,
            context: Context,
        ): IO[Unit] = IO.unit

        def recordResponseBodySize(
            request: MetricsRequest,
            response: ResponsePrelude,
            terminationType: Option[TerminationType],
            bodySizeBytes: Long,
            context: Context,
        ): IO[Unit] = IO.unit
      }
      response <- Metrics[IO](ops)(HttpApp.pure[IO](Response[IO](Status.NoContent)))
        .run(Request[IO]())
      firstResult <- response.body.compile.drain.attempt
      secondResult <- response.body.compile.drain.attempt
      activeRequests <- active.get
      attempts <- totalAttempts.get
    } yield {
      assertEquals(firstResult, Left(failure))
      assertEquals(secondResult, Right(()))
      assertEquals(activeRequests, 0L)
      assertEquals(attempts, 1)
    }
  }
}
