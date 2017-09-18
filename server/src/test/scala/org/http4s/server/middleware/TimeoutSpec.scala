package org.http4s
package server
package middleware

import cats.effect._
import cats.implicits._
import fs2.Scheduler
import java.util.concurrent.atomic.AtomicBoolean
import org.http4s.dsl.io._
import scala.concurrent.duration._

class TimeoutSpec extends Http4sSpec {

  val myService = HttpService[IO] {
    case _ -> Root / "fast" =>
      Ok("Fast")
    case _ -> Root / "slow" =>
      delay(2.seconds, Ok("Slow"))
  }

  val timeoutService = Timeout(5.milliseconds)(myService)

  val fastReq = Request[IO](GET, uri("/fast"))
  val slowReq = Request[IO](GET, uri("/slow"))

  "Timeout Middleware" should {
    "have no effect if the response is not delayed" in {
      timeoutService.orNotFound(fastReq) must returnStatus(Status.Ok)
    }

    "return a 500 error if the result takes too long" in {
      timeoutService.orNotFound(slowReq) must returnStatus(Status.InternalServerError)
    }

    "return the provided response if the result takes too long" in {
      val customTimeout = Response[IO](Status.GatewayTimeout) // some people return 504 here.
      val altTimeoutService = Timeout(1.nanosecond, IO.pure(customTimeout))(myService)
      altTimeoutService.orNotFound(slowReq) must returnStatus(customTimeout.status)
    }

    "handle infinite durations" in {
      val service = Timeout(Duration.Inf)(myService)
      service.orNotFound(slowReq) must returnStatus(Status.Ok)
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
      timeoutService.orNotFound(Request[IO]()) must returnStatus(InternalServerError)
      // Give the losing response enough time to finish
      clean.get must beTrue.eventually
    }
  }

  private val scheduler = Scheduler.allocate[IO](corePoolSize = 1).map(_._1).unsafeRunSync()

  private def delay[F[_]: Effect, A](duration: FiniteDuration, fa: F[A]): F[A] =
    scheduler.sleep_(duration).run.followedBy(fa)
}
