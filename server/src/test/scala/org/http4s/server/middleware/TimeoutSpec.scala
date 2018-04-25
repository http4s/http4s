package org.http4s
package server
package middleware

import cats.effect._
import cats.implicits._
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import org.http4s.dsl.io._
import scala.concurrent.CancellationException
import scala.concurrent.duration._

class TimeoutSpec extends Http4sSpec {

  val routes = HttpRoutes.of[IO] {
    case _ -> Root / "fast" =>
      Ok("Fast")

    case _ -> Root / "never" =>
      IO.async[Response[IO]] { _ =>
        ()
      }
  }

  val app = Timeout(5.milliseconds)(routes).orNotFound

  val fastReq = Request[IO](GET, uri("/fast"))
  val neverReq = Request[IO](GET, uri("/never"))

  def checkStatus(resp: IO[Response[IO]], status: Status) =
    resp.unsafeRunTimed(3.seconds).getOrElse(throw new TimeoutException) must haveStatus(status)

  "Timeout Middleware" should {
    "have no effect if the response is timely" in {
      val app = Timeout(365.days)(routes).orNotFound
      checkStatus(app(fastReq), Status.Ok)
    }

    "return a 500 error if the result takes too long" in {
      checkStatus(app(neverReq), Status.InternalServerError)
    }

    "return the provided response if the result takes too long" in {
      val customTimeout = Response[IO](Status.GatewayTimeout) // some people return 504 here.
      val altTimeoutService = Timeout(1.nanosecond, IO.pure(customTimeout))(routes)
      checkStatus(altTimeoutService.orNotFound(neverReq), customTimeout.status)
    }

    "cancel the loser" in {
      val canceled = new AtomicBoolean(false)
      val cancellationException = new CancellationException()
      val routes = HttpRoutes.of[IO] {
        case _ =>
          IO.sleep(2.seconds).onCancelRaiseError(cancellationException).attempt.flatMap {
            case Left(`cancellationException`) => IO(canceled.set(true)) *> NoContent()
            case _ => NoContent()
          }
      }
      val app = Timeout(1.millis)(routes).orNotFound
      checkStatus(app(Request[IO]()), InternalServerError)
      // Give the losing response enough time to finish
      canceled.get must beTrue.eventually
    }
  }
}
