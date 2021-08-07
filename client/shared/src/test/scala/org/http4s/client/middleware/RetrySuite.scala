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

package org.http4s
package client
package middleware

import cats.effect.kernel.Ref
import cats.effect.std.Semaphore
import cats.effect.{IO, Resource}
import cats.syntax.all._
import fs2.Stream
import org.http4s.dsl.io._
import org.http4s.headers.`Idempotency-Key`
import org.http4s.laws.discipline.ArbitraryInstances.http4sTestingArbitraryForStatus
import org.http4s.syntax.all._
import org.scalacheck.effect.PropF
import org.scalacheck.Gen
import scala.concurrent.duration._

class RetrySuite extends Http4sSuite {
  val app = HttpRoutes
    .of[IO] {
      case req @ _ -> Root / "status-from-body" =>
        req.as[String].flatMap {
          case "OK" => Ok()
          case "" => InternalServerError()
        }
      case _ -> Root / status =>
        IO.pure(Response(Status.fromInt(status.toInt).valueOr(throw _)))
    }
    .orNotFound

  val defaultClient: Client[IO] = Client.fromHttpApp(app)

  def countRetries(
      client: Client[IO],
      method: Method,
      status: Status,
      body: EntityBody[IO]): IO[Int] = {
    val max = 2
    var attemptsCounter = 1
    val policy = RetryPolicy[IO] { (attempts: Int) =>
      if (attempts >= max) None
      else {
        attemptsCounter = attemptsCounter + 1
        Some(10.milliseconds)
      }
    }
    val retryClient = Retry[IO](policy)(client)
    val req = Request[IO](method, uri"http://localhost/" / status.code.toString).withEntity(body)
    retryClient
      .run(req)
      .use { _ =>
        IO.unit
      }
      .attempt
      .map(_ => attemptsCounter)
  }

  test("default retriable should ggretry GET based on status code") {
    List(
      (Ok, 1),
      (Found, 1),
      (BadRequest, 1),
      (NotFound, 1),
      (RequestTimeout, 2),
      (InternalServerError, 2),
      (NotImplemented, 1),
      (BadGateway, 2),
      (ServiceUnavailable, 2),
      (GatewayTimeout, 2),
      (HttpVersionNotSupported, 1)
    ).traverse { case (s, r) => countRetries(defaultClient, GET, s, EmptyBody).assertEquals(r) }
  }

  test("default retriable should not retry non-idempotent methods") {
    PropF.forAllF { (s: Status) =>
      countRetries(defaultClient, POST, s, EmptyBody).assertEquals(1)
    }
  }

  test("is error or retriable status should return true on a response with a retriable status") {
    val statusGen = Gen.oneOf(RetryPolicy.RetriableStatuses)
    PropF.forAllF[IO, Status, IO[Unit]](statusGen) { status =>
      IO.pure(
        RetryPolicy.isErrorOrRetriableStatus(Response[IO](status).asRight)
      ).assertEquals(true)
    }
  }

  test("is error or status should return true on a response with a supported status") {
    val statusGen = Gen.oneOf(Status.registered)
    PropF.forAllF[IO, Status, IO[Unit]](statusGen) { status =>
      IO.pure(
        RetryPolicy.isErrorOrStatus(Response[IO](status).asRight, Set(status))
      ).assertEquals(true)
    }
  }

  test(
    "is error or status should return false when a response does not match the supported status") {
    val statusGen = Gen.oneOf(Status.registered)
    PropF.forAllF[IO, Status, IO[Unit]](statusGen) { status =>
      IO.pure(
        RetryPolicy.isErrorOrStatus(Response[IO](status).asRight, Set.empty[Status])
      ).assertEquals(false)
    }
  }

  def resubmit(method: Method, headers: Headers = Headers.empty)(
      retriable: (Request[IO], Either[Throwable, Response[IO]]) => Boolean) =
    Ref[IO]
      .of(false)
      .flatMap { ref =>
        val body = Stream.eval(ref.get.flatMap {
          case false => ref.update(_ => true) *> IO.pure("")
          case true => IO.pure("OK")
        })
        val req = Request[IO](method, uri"http://localhost/status-from-body")
          .withHeaders(headers)
          .withEntity(body)
        val policy = RetryPolicy[IO](
          (attempts: Int) =>
            if (attempts >= 2) None
            else Some(Duration.Zero),
          retriable)
        val retryClient = Retry[IO](policy)(defaultClient)
        retryClient.status(req)
      }

  test("default retriable should defaultRetriable does not resubmit bodies on idempotent methods") {
    resubmit(POST)(RetryPolicy.defaultRetriable).assertEquals(Status.InternalServerError)
  }
  test("default retriable should defaultRetriable resubmits bodies on idempotent header") {
    resubmit(POST, Headers(`Idempotency-Key`("key")))(RetryPolicy.defaultRetriable)
      .assertEquals(Status.Ok)
  }
  test("default retriable should defaultRetriable resubmits bodies on idempotent methods") {
    resubmit(PUT)(RetryPolicy.defaultRetriable).assertEquals(Status.Ok)
  }
  test("default retriable should recklesslyRetriable resubmits bodies on non-idempotent methods") {
    resubmit(POST)((_, result) => RetryPolicy.recklesslyRetriable(result)).assertEquals(Status.Ok)
  }

  test("default retriable should retry exceptions") {
    val failClient = Client[IO](_ => Resource.eval(IO.raiseError(new Exception("boom"))))
    countRetries(failClient, GET, InternalServerError, EmptyBody).assertEquals(2)
  }

  test("default retriable should not retry a TimeoutException") {
    val failClient = Client[IO](_ => Resource.eval(IO.raiseError(WaitQueueTimeoutException)))
    countRetries(failClient, GET, InternalServerError, EmptyBody).assertEquals(1)
  }

  test("default retriable should not exhaust the connection pool on retry") {
    Semaphore[IO](2)
      .flatMap { semaphore =>
        val client = Retry[IO](
          RetryPolicy(
            (att =>
              if (att < 3) Some(Duration.Zero)
              else None),
            RetryPolicy.defaultRetriable[IO]))(Client[IO](_ =>
          Resource.make(semaphore.tryAcquire.flatMap {
            case true => Response[IO](Status.InternalServerError).pure[IO]
            case false => IO.raiseError(new IllegalStateException("Exhausted all connections"))
          })(_ => semaphore.release)))
        client.status(Request[IO]())
      }
      .assertEquals(Status.InternalServerError)
  }
}
