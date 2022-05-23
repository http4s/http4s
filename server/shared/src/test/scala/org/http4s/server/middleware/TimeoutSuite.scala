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
package server
package middleware

import cats.data.OptionT
import cats.effect._
import org.http4s.dsl.io._
import org.http4s.syntax.all._

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.TimeoutException
import scala.concurrent.duration._

class TimeoutSuite extends Http4sSuite {
  // To distinguish from the inherited cats-effect-testing Timeout
  import org.http4s.server.middleware.{Timeout => TimeoutMiddleware}

  private val defaultRoutes = HttpRoutes.of[IO] {
    case _ -> Root / "fast" =>
      Ok("Fast")

    case _ -> Root / "never" =>
      IO.never[Response[IO]]

    case _ -> Root / "uncancelable" =>
      IO.uncancelable(_ => IO.sleep(3100.milliseconds)) *> Ok("uncancelable")
  }

  private val fastReq = Request[IO](GET, uri"/fast")
  private val neverReq = Request[IO](GET, uri"/never")
  private val uncancelableReq = Request[IO](GET, uri"/uncancelable")

  def checkStatus(resp: IO[Response[IO]], status: Status): IO[Unit] =
    resp.map(_.status).timeout(3.seconds).assertEquals(status)

  def testMiddleware(timeout: FiniteDuration, routes: HttpRoutes[IO] = defaultRoutes)(
      test: Http[IO, IO] => IO[Unit]
  ) =
    for {
      _ <- test(TimeoutMiddleware(timeout)(routes).orNotFound)
      _ <- test(TimeoutMiddleware.httpRoutes(timeout)(routes).orNotFound)
      _ <- test(TimeoutMiddleware.httpApp(timeout)(routes.orNotFound))
    } yield ()

  def testMiddlewareWithResponse(
      timeout: FiniteDuration,
      response: Response[IO],
      routes: HttpRoutes[IO] = defaultRoutes,
  )(test: Http[IO, IO] => IO[Unit]) =
    for {
      _ <- test(TimeoutMiddleware(timeout, OptionT.pure[IO](response))(routes).orNotFound)
      _ <- test(TimeoutMiddleware.httpRoutes(timeout, IO.pure(response))(routes).orNotFound)
      _ <- test(TimeoutMiddleware.httpApp(timeout, IO.pure(response))(routes.orNotFound))
    } yield ()

  test("have no effect if the response is timely") {
    testMiddleware(365.days) { app =>
      checkStatus(app(fastReq), Status.Ok)
    }
  }

  test("return a 503 error if the result takes too long") {
    testMiddleware(5.milliseconds) { app =>
      checkStatus(app(neverReq), Status.ServiceUnavailable)
    }
  }

  test(
    "return a 503 error if the result takes too long and execute underlying uncancelable effect"
  ) {
    testMiddleware(5.milliseconds) { app =>
      for {
        _ <- app(uncancelableReq).map(_.status).assertEquals(Status.ServiceUnavailable)
        _ <- checkStatus(app(uncancelableReq), Status.ServiceUnavailable)
          .intercept[TimeoutException]
      } yield ()
    }
  }

  test("return the provided response if the result takes too long") {
    val customTimeout = Response[IO](Status.GatewayTimeout) // some people return 504 here.

    testMiddlewareWithResponse(1.nanosecond, customTimeout) { app =>
      checkStatus(app(neverReq), customTimeout.status)
    }
  }

  test("cancel the loser") {
    val canceled = new AtomicBoolean(false)
    val routes = HttpRoutes.of[IO] { case _ =>
      IO.never.guarantee(IO(canceled.set(true)))
    }

    testMiddleware(1.millisecond, routes) { app =>
      checkStatus(app(Request[IO]()), Status.ServiceUnavailable) *>
        // Give the losing response enough time to finish
        IO.sleep(100.milliseconds) *> IO(canceled.get).map(_ => ())
    }
  }
}
