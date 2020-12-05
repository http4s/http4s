/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package client
package middleware

import cats.effect.{IO, Resource}
import cats.effect.concurrent.{Ref, Semaphore}
import cats.syntax.all._
import fs2.Stream
import org.http4s.Uri.uri
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import org.http4s.laws.discipline.ArbitraryInstances._
import scala.concurrent.duration._
import org.scalacheck.effect.PropF

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
    val req = Request[IO](method, uri("http://localhost/") / status.code.toString).withEntity(body)
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

  test("default retriable should ggnot retry non-idempotent methods") {
    PropF.forAllF { (s: Status) =>
      countRetries(defaultClient, POST, s, EmptyBody).assertEquals(1)
    }
  }

  def resubmit(method: Method)(
      retriable: (Request[IO], Either[Throwable, Response[IO]]) => Boolean) =
    Ref[IO]
      .of(false)
      .flatMap { ref =>
        val body = Stream.eval(ref.get.flatMap {
          case false => ref.update(_ => true) *> IO.pure("")
          case true => IO.pure("OK")
        })
        val req = Request[IO](method, uri("http://localhost/status-from-body")).withEntity(body)
        val policy = RetryPolicy[IO](
          (attempts: Int) =>
            if (attempts >= 2) None
            else Some(Duration.Zero),
          retriable)
        val retryClient = Retry[IO](policy)(defaultClient)
        retryClient.status(req)
      }

  test(
    "default retriable should ggdefaultRetriable does not resubmit bodies on idempotent methods") {
    resubmit(POST)(RetryPolicy.defaultRetriable).assertEquals(Status.InternalServerError)
  }
  test("default retriable should ggdefaultRetriable resubmits bodies on idempotent methods") {
    resubmit(PUT)(RetryPolicy.defaultRetriable).assertEquals(Status.Ok)
  }
  test(
    "default retriable should ggrecklesslyRetriable resubmits bodies on non-idempotent methods") {
    resubmit(POST)((_, result) => RetryPolicy.recklesslyRetriable(result)).assertEquals(Status.Ok)
  }

  test("default retriable should ggretry exceptions") {
    val failClient = Client[IO](_ => Resource.liftF(IO.raiseError(new Exception("boom"))))
    countRetries(failClient, GET, InternalServerError, EmptyBody).assertEquals(2)
  }

  test("default retriable should ggnot retry a TimeoutException") {
    val failClient = Client[IO](_ => Resource.liftF(IO.raiseError(WaitQueueTimeoutException)))
    countRetries(failClient, GET, InternalServerError, EmptyBody).assertEquals(1)
  }

  test("default retriable should ggnot exhaust the connection pool on retry") {
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
