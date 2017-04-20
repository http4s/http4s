package org.http4s
package server
package middleware

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._
import fs2._
import fs2.Stream._
import fs2.time._
import org.http4s.Http4sSpec._
import org.http4s.dsl._

class TimeoutSpec extends Http4sSpec {

  val myService = HttpService {
    case _ -> Root / "fast" =>
      Ok("Fast")
    case _ -> Root / "slow" =>
      Ok("Slow").async(TestScheduler.delayedStrategy(2.seconds))
  }

  val timeoutService = Timeout.apply(1.nanosecond)(myService)

  val fastReq = Request(GET, uri("/fast"))
  val slowReq = Request(GET, uri("/slow"))

  "Timeout Middleware" should {
    "have no effect if the response is not delayed" in {
      timeoutService.orNotFound(fastReq) must returnStatus (Status.Ok)
    }

    "return a 500 error if the result takes too long" in {
      timeoutService.orNotFound(slowReq) must returnStatus (Status.InternalServerError)
    }

    "return the provided response if the result takes too long" in {
      val customTimeout = Response(Status.GatewayTimeout) // some people return 504 here.
      val altTimeoutService = Timeout(1.nanosecond, Task.now(customTimeout))(myService)
      altTimeoutService.orNotFound(slowReq) must returnStatus (customTimeout.status)
    }

    "handle infinite durations" in {
      val service = Timeout(Duration.Inf)(myService)
      service.orNotFound(slowReq) must returnStatus (Status.Ok)
    }

    "clean up resources of the loser" in {
      var clean = new AtomicBoolean(false)
      val service = HttpService {
        case _ =>
          for {
            resp <- NoContent().schedule(2.seconds)
            _    <- Task.delay(clean.set(true))
          } yield resp
      }
      val timeoutService = Timeout(1.millis)(service)
      timeoutService.orNotFound(Request()) must returnStatus (InternalServerError)
      // Give the losing response enough time to finish
      clean.get must beTrue.eventually
    }
  }
}
