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
import org.http4s.headers.`Content-Length`
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
    val request = Request[IO](method = Method.POST, uri = uri"/metrics")
      .withBodyStream(Stream.emits("request".getBytes).covary[IO])
    val client =
      Client[IO](request =>
        Resource.eval(
          request.body.compile.drain.as(
            Response[IO](Status.Created)
              .withBodyStream(Stream.emits("response".getBytes).covary[IO])
          )
        )
      )

    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(client).run(request).use(_.body.compile.drain)
      state <- ops.state
    } yield {
      assertEquals(state.active, 0L)
      assertEquals(state.contexts, List(request.requestPrelude -> Some("POST")))
      assertEquals(state.connectionInfos, List(None))
      assertEquals(state.increases, state.contexts)
      assertEquals(state.decreases, state.contexts)
      assertEquals(state.headers.map(_._1), List(request.requestPrelude))
      assertEquals(state.headers.map(_._3), List(Some("POST")))
      assertEquals(state.totals.flatMap(_.response.map(_.status)), List(Status.Created))
      assertEquals(state.totals.map(_.terminationType), List(None))
      assertEquals(state.totals.map(_.context), List(Some("POST")))
      assertEquals(state.requestBodies.map(_.request), List(request.requestPrelude))
      assertEquals(state.requestBodies.map(_.bodySizeBytes), List(7L))
      assertEquals(state.responseBodies.flatMap(_.response.map(_.status)), List(Status.Created))
      assertEquals(state.responseBodies.map(_.bodySizeBytes), List(8L))
      assertEquals(state.requestBodies.map(_.context), List(Some("POST")))
      assertEquals(state.responseBodies.map(_.context), List(Some("POST")))
    }
  }

  test("MetricsOps2 records empty bodies as zero bytes") {
    val client = Client[IO]((_: Request[IO]) => Resource.pure(Response[IO](Status.NoContent)))

    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(client).run(req).use(_.body.compile.drain)
      state <- ops.state
    } yield {
      assertEquals(state.requestBodies.map(_.bodySizeBytes), List(0L))
      assertEquals(state.responseBodies.map(_.bodySizeBytes), List(0L))
    }
  }

  test("MetricsOps2 bypasses instrumentation when createContext returns None") {
    val client = Client[IO]((request: Request[IO]) =>
      Resource.eval(request.body.compile.drain.as(Response[IO](Status.Accepted)))
    )
    val request = Request[IO]().withEntity("request")

    for {
      ops <- TestMetricsOps2.create(_ => false)
      response <- Metrics[IO](ops)(client).run(request).use(IO.pure)
      state <- ops.state
    } yield {
      assertEquals(response.status, Status.Accepted)
      assertEquals(state, TestMetricsOps2.State.empty)
    }
  }

  test("MetricsOps2 uses Content-Length after the body stream completes") {
    val request = Request[IO](method = Method.POST, uri = uri"/metrics")
      .withBodyStream(Stream.emits("request".getBytes).covary[IO])
      .putHeaders(`Content-Length`.unsafeFromLong(70L))
    val client = Client[IO](request =>
      Resource.eval(
        request.body.compile.drain.as(
          Response[IO](Status.Ok)
            .withBodyStream(Stream.emits("response".getBytes).covary[IO])
            .putHeaders(`Content-Length`.unsafeFromLong(80L))
        )
      )
    )

    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(client).run(request).use(_.body.compile.drain)
      state <- ops.state
    } yield {
      assertEquals(state.requestBodies.map(_.bodySizeBytes), List(70L))
      assertEquals(state.responseBodies.map(_.bodySizeBytes), List(80L))
    }
  }

  test("MetricsOps2 treats a HEAD response body as empty despite Content-Length") {
    val request = Request[IO](method = Method.HEAD, uri = uri"/metrics")
    val client = Client[IO]((_: Request[IO]) =>
      Resource.pure(
        Response[IO](Status.Ok).putHeaders(`Content-Length`.unsafeFromLong(80L))
      )
    )

    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(client).run(request).use(_.body.compile.drain)
      state <- ops.state
    } yield assertEquals(state.responseBodies.map(_.bodySizeBytes), List(0L))
  }

  test("MetricsOps2 omits an unconsumed response body with Content-Length") {
    val client = Client[IO]((_: Request[IO]) =>
      Resource.pure(
        Response[IO](Status.Ok)
          .withBodyStream(Stream.emits("response".getBytes).covary[IO])
          .putHeaders(`Content-Length`.unsafeFromLong(8L))
      )
    )

    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(client).status(req)
      state <- ops.state
    } yield assertEquals(state.responseBodies, Nil)
  }

  test("MetricsOps2 records zero when the response body is not consumed") {
    val client = Client[IO]((_: Request[IO]) =>
      Resource.pure(
        Response[IO](Status.Ok).withBodyStream(Stream.emits("response".getBytes).covary[IO])
      )
    )

    for {
      ops <- TestMetricsOps2.create
      status <- Metrics[IO](ops)(client).status(req)
      state <- ops.state
    } yield {
      assertEquals(status, Status.Ok)
      assertEquals(state.totals.flatMap(_.response.map(_.status)), List(Status.Ok))
      assertEquals(state.responseBodies.map(_.bodySizeBytes), List(0L))
    }
  }

  test("MetricsOps2 records bytes from a partially consumed response body") {
    val client = Client[IO]((_: Request[IO]) =>
      Resource.pure(
        Response[IO](Status.Ok).withBodyStream(
          Stream.emits("response".getBytes).flatMap(Stream.emit(_)).covary[IO]
        )
      )
    )

    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(client).run(req).use(_.body.take(1).compile.drain)
      state <- ops.state
    } yield {
      assertEquals(state.totals.flatMap(_.response.map(_.status)), List(Status.Ok))
      assertEquals(state.responseBodies.map(_.bodySizeBytes), List(1L))
    }
  }

  test("MetricsOps2 records a replayable response body size once") {
    val client = Client[IO]((_: Request[IO]) =>
      Resource.pure(
        Response[IO](Status.Ok).withBodyStream(Stream.emits("response".getBytes).covary[IO])
      )
    )

    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(client).run(req).use { response =>
        response.body.compile.drain >> response.body.compile.drain
      }
      state <- ops.state
    } yield assertEquals(state.responseBodies.map(_.bodySizeBytes), List(8L))
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
      assertEquals(state.contexts.size, 1)
      assertEquals(state.increases, state.contexts)
      assertEquals(state.decreases, state.contexts)
      assertEquals(state.headers, Nil)
      assertEquals(state.totals.map(_.response), List(None))
      assertEquals(state.totals.map(_.terminationType), List(Some(Canceled)))
      assertEquals(state.requestBodies.map(_.request), List(req.requestPrelude))
      assertEquals(state.responseBodies, Nil)
    }
  }

  test("MetricsOps2 omits a declared-length request body that is not evaluated") {
    val failure = new RuntimeException("request failed")
    val request = Request[IO](method = Method.POST)
      .withBodyStream(Stream.emits("request".getBytes).covary[IO])
      .putHeaders(`Content-Length`.unsafeFromLong(70L))
    val client = Client[IO]((_: Request[IO]) => Resource.eval(IO.raiseError(failure)))

    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(client).run(request).use_.attempt
      state <- ops.state
    } yield assertEquals(state.requestBodies, Nil)
  }

  test("MetricsOps2 omits a declared-length response body that fails") {
    val failure = new RuntimeException("body failed")
    val client = Client[IO]((_: Request[IO]) =>
      Resource.pure(
        Response[IO](Status.Ok)
          .withBodyStream(
            Stream.emits("response".getBytes).covary[IO] ++ Stream.raiseError[IO](failure)
          )
          .putHeaders(`Content-Length`.unsafeFromLong(80L))
      )
    )

    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(client).run(req).use(_.body.compile.drain.attempt.void)
      state <- ops.state
    } yield {
      assertEquals(state.responseBodies, Nil)
      assertEquals(state.totals.map(_.terminationType), List(Some(TerminationType.Error(failure))))
    }
  }


  test("MetricsOps2 records an error before a response without headers or response size") {
    val failure = new RuntimeException("boom")
    val client = Client[IO]((_: Request[IO]) => Resource.eval(IO.raiseError(failure)))

    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(client).run(req).use_.attempt
      state <- ops.state
    } yield {
      assertEquals(state.active, 0L)
      assertEquals(state.headers, Nil)
      assertEquals(state.totals.map(_.response), List(None))
      assertEquals(state.totals.map(_.terminationType), List(Some(TerminationType.Error(failure))))
      assertEquals(state.requestBodies.map(_.bodySizeBytes), List(0L))
      assertEquals(state.responseBodies, Nil)
    }
  }

  test("MetricsOps2 retains a response body error recovered by the resource consumer") {
    val failure = new RuntimeException("body failed")
    val client = Client[IO]((_: Request[IO]) =>
      Resource.pure(
        Response[IO](Status.Ok).withBodyStream(Stream.raiseError[IO](failure))
      )
    )

    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(client).run(req).use(_.body.compile.drain.attempt.void)
      state <- ops.state
    } yield {
      assertEquals(state.active, 0L)
      assertEquals(state.totals.map(_.terminationType), List(Some(TerminationType.Error(failure))))
      assertEquals(
        state.requestBodies.map(_.terminationType),
        List(Some(TerminationType.Error(failure))),
      )
      assertEquals(state.responseBodies.map(_.bodySizeBytes), List(0L))
    }
  }

  test("MetricsOps2 records an unrecovered response body error once") {
    val failure = new RuntimeException("body failed")
    val client = Client[IO]((_: Request[IO]) =>
      Resource.pure(
        Response[IO](Status.Ok).withBodyStream(Stream.raiseError[IO](failure))
      )
    )

    for {
      ops <- TestMetricsOps2.create
      result <- Metrics[IO](ops)(client).run(req).use(_.body.compile.drain).attempt
      state <- ops.state
    } yield {
      assertEquals(result, Left(failure))
      assertEquals(state.active, 0L)
      assertEquals(state.totals.map(_.terminationType), List(Some(TerminationType.Error(failure))))
    }
  }

  test("MetricsOps2 retains a response body cleanup error recovered by the resource consumer") {
    val failure = new RuntimeException("body cleanup failed")
    val body = Stream.emit(0.toByte).covary[IO].onFinalize(IO.raiseError[Unit](failure))
    val client =
      Client[IO]((_: Request[IO]) => Resource.pure(Response[IO](Status.Ok).withBodyStream(body)))

    for {
      ops <- TestMetricsOps2.create
      _ <- Metrics[IO](ops)(client).run(req).use(_.body.compile.drain.attempt.void)
      state <- ops.state
    } yield {
      assertEquals(state.active, 0L)
      assertEquals(state.totals.map(_.terminationType), List(Some(TerminationType.Error(failure))))
    }
  }

  test("MetricsOps2 records a resource consumer error after a successful response body") {
    val failure = new RuntimeException("consumer failed")
    val client = Client[IO]((_: Request[IO]) =>
      Resource.pure(
        Response[IO](Status.Ok).withBodyStream(Stream.emit(0.toByte).covary[IO])
      )
    )

    for {
      ops <- TestMetricsOps2.create
      result <- Metrics[IO](ops)(client)
        .run(req)
        .use(response => response.body.compile.drain >> IO.raiseError[Unit](failure))
        .attempt
      state <- ops.state
    } yield {
      assertEquals(result, Left(failure))
      assertEquals(state.active, 0L)
      assertEquals(state.totals.map(_.terminationType), List(Some(TerminationType.Error(failure))))
    }
  }

  test("MetricsOps2 prefers a response body error to a later resource consumer error") {
    val bodyFailure = new RuntimeException("body failed")
    val consumerFailure = new RuntimeException("consumer failed")
    val client = Client[IO]((_: Request[IO]) =>
      Resource.pure(
        Response[IO](Status.Ok).withBodyStream(Stream.raiseError[IO](bodyFailure))
      )
    )

    for {
      ops <- TestMetricsOps2.create
      result <- Metrics[IO](ops)(client)
        .run(req)
        .use(response =>
          response.body.compile.drain.attempt >> IO.raiseError[Unit](consumerFailure)
        )
        .attempt
      state <- ops.state
    } yield {
      assertEquals(result, Left(consumerFailure))
      assertEquals(state.active, 0L)
      assertEquals(
        state.totals.map(_.terminationType),
        List(Some(TerminationType.Error(bodyFailure))),
      )
    }
  }

  test("MetricsOps2 records response body cancellation once") {
    for {
      ready <- Deferred[IO, Unit]
      ops <- TestMetricsOps2.create
      client = Client[IO]((_: Request[IO]) =>
        Resource.pure(
          Response[IO](Status.Ok)
            .withBodyStream(Stream.eval(ready.complete(()) >> IO.never[Byte]))
        )
      )
      fiber <- Metrics[IO](ops)(client).run(req).use(_.body.compile.drain).start
      _ <- ready.get
      _ <- fiber.cancel
      _ <- fiber.join
      state <- ops.state
    } yield {
      assertEquals(state.active, 0L)
      assertEquals(state.totals.map(_.terminationType), List(Some(Canceled)))
    }
  }
}
