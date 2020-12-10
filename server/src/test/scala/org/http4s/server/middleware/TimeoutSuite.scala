/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package server
package middleware

import cats.data.OptionT
import cats.effect._
import java.util.concurrent.atomic.AtomicBoolean
import org.http4s.Uri.uri
import org.http4s.dsl.io._
import org.http4s.syntax.all._
import scala.concurrent.duration._

class TimeoutSuite extends Http4sSuite {
  // To distinguish from the inherited cats-effect-testing Timeout
  import org.http4s.server.middleware.{Timeout => TimeoutMiddleware}

  val routes = HttpRoutes.of[IO] {
    case _ -> Root / "fast" =>
      Ok("Fast")

    case _ -> Root / "never" =>
      IO.never[Response[IO]]
  }

  val app = TimeoutMiddleware(5.milliseconds)(routes).orNotFound

  val fastReq = Request[IO](GET, uri("/fast"))
  val neverReq = Request[IO](GET, uri("/never"))

  def checkStatus(resp: IO[Response[IO]], status: Status): IO[Unit] =
    IO.race(IO.sleep(3.seconds), resp.map(_.status)).assertEquals(Right(status))

  test("have no effect if the response is timely") {
    val app = TimeoutMiddleware(365.days)(routes).orNotFound
    checkStatus(app(fastReq), Status.Ok)
  }

  test("return a 503 error if the result takes too long") {
    checkStatus(app(neverReq), Status.ServiceUnavailable)
  }

  test("return the provided response if the result takes too long") {
    val customTimeout = Response[IO](Status.GatewayTimeout) // some people return 504 here.
    val altTimeoutService =
      TimeoutMiddleware(1.nanosecond, OptionT.pure[IO](customTimeout))(routes)
    checkStatus(altTimeoutService.orNotFound(neverReq), customTimeout.status)
  }

  test("cancel the loser") {
    val canceled = new AtomicBoolean(false)
    val routes = HttpRoutes.of[IO] { case _ =>
      IO.never.guarantee(IO(canceled.set(true)))
    }
    val app = TimeoutMiddleware(1.millis)(routes).orNotFound
    checkStatus(app(Request[IO]()), Status.ServiceUnavailable) *>
      // Give the losing response enough time to finish
      IO.sleep(100.milliseconds) *> IO(canceled.get)
  }
}
