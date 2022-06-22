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
import cats.effect.testkit.TestControl
import org.http4s.dsl.io._
import org.http4s.laws.discipline.arbitrary.genFiniteDuration
import org.http4s.syntax.all._
import org.scalacheck.effect.PropF.forAllF

import java.util.concurrent.atomic.AtomicBoolean
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
      IO.uncancelable(_ => IO.sleep(1100.milliseconds)) *> Ok("uncancelable")
  }

  private val fastReq = Request[IO](GET, uri"/fast")
  private val neverReq = Request[IO](GET, uri"/never")
  private val uncancelableReq = Request[IO](GET, uri"/uncancelable")

  def checkStatus(resp: IO[Response[IO]], status: Status): IO[Unit] =
    resp.map(_.status).assertEquals(status)

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
    forAllF(genFiniteDuration) { (timeOut: FiniteDuration) =>
      val prog = testMiddleware(timeOut) { app =>
        checkStatus(app(fastReq), Status.Ok)
      }

      TestControl.executeEmbed(prog)
    }
  }

  test("fail with a timeout error if the response takes longer than the timeout duration") {
    forAllF(genFiniteDuration) { (timeOut: FiniteDuration) =>
      val prog = testMiddleware(timeOut) { app =>
        checkStatus(app(neverReq), Status.ServiceUnavailable)
      }

      TestControl.executeEmbed(prog)
    }
  }

  test(
    "return a 503 error if the result takes too long and execute the underlying uncancelable effect anyway"
  ) {
    forAllF(genFiniteDuration) { (timeOut: FiniteDuration) =>
      // 1100 millis is the hard coded response time of uncancelableReq
      val fixed = timeOut.min(1099.milliseconds)
      val prog = testMiddleware(fixed) { app =>
        checkStatus(app(uncancelableReq), Status.ServiceUnavailable)
      }

      TestControl.executeEmbed(prog)

    }
  }

  test("return the provided response if the result takes too long") {
    forAllF(genFiniteDuration) { (timeOut: FiniteDuration) =>
      val customTimeout = Response[IO](Status.GatewayTimeout) // some people return 504 here.

      val prog = testMiddlewareWithResponse(timeOut, customTimeout) { app =>
        checkStatus(app(neverReq), customTimeout.status)
      }

      TestControl.executeEmbed(prog)
    }
  }

  test("cancel the loser") {
    forAllF(genFiniteDuration) { (timeOut: FiniteDuration) =>
      val canceled = new AtomicBoolean(false)
      val routes = HttpRoutes.of[IO] { case _ =>
        IO.never.guarantee(IO(canceled.set(true)))
      }

      val prog = testMiddleware(timeOut, routes) { app =>
        checkStatus(app(Request[IO]()), Status.ServiceUnavailable) *>
          // Give the losing response enough time to finish
          IO.sleep(100.milliseconds) *> IO(canceled.get).map(_ => ())
      }

      TestControl.executeEmbed(prog)
    }
  }
}
