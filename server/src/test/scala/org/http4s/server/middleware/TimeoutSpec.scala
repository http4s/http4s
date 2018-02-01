package org.http4s
package server
package middleware

import cats.effect._
import cats.implicits._
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import org.http4s.dsl.io._
import scala.concurrent.duration._

class TimeoutSpec extends Http4sSpec {

  val myService = HttpService[IO] {
    case _ -> Root / "fast" =>
      Ok("Fast")

    case _ -> Root / "never" =>
      IO.async[Response[IO]] { _ =>
        ()
      }
  }

  val timeoutService = Timeout(5.milliseconds)(myService)

  val fastReq = Request[IO](GET, uri("/fast"))
  val neverReq = Request[IO](GET, uri("/never"))

  def checkStatus(resp: IO[Response[IO]], status: Status) =
    resp.unsafeRunTimed(3.seconds).getOrElse(throw new TimeoutException) must haveStatus(status)

  "Timeout Middleware" should {
    "have no effect if the response is not delayed" in {
      val service = Timeout(Duration.Inf)(myService)
      checkStatus(service.orNotFound(fastReq), Status.Ok)
    }

    "return a 500 error if the result takes too long" in {
      checkStatus(timeoutService.orNotFound(neverReq), Status.InternalServerError)
    }

    "return the provided response if the result takes too long" in {
      val customTimeout = Response[IO](Status.GatewayTimeout) // some people return 504 here.
      val altTimeoutService = Timeout(1.nanosecond, IO.pure(customTimeout))(myService)
      checkStatus(altTimeoutService.orNotFound(neverReq), customTimeout.status)
    }

    "clean up resources of the loser" in {
      val clean = new AtomicBoolean(false)
      val service = HttpService[IO] {
        case _ =>
          for {
            resp <- delay(2.seconds, NoContent())
            _ <- IO(clean.set(true))
          } yield resp
      }
      val timeoutService = Timeout(1.millis)(service)
      checkStatus(timeoutService.orNotFound(Request[IO]()), InternalServerError)
      // Give the losing response enough time to finish
      clean.get must beTrue.eventually
    }
  }

  private def delay[F[_]: Effect, A](duration: FiniteDuration, fa: F[A]): F[A] =
    Http4sSpec.TestScheduler.sleep_(duration).compile.drain *> fa
}
