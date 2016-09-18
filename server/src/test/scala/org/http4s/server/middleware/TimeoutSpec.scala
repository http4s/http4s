package org.http4s
package server
package middleware

import scala.concurrent.duration._

import fs2._
import org.http4s.Http4sSpec._
import org.http4s.dsl._

class TimeoutSpec extends Http4sSpec {

  val myService = HttpService {
    case _ -> Root / "fast" =>
      Ok("Fast")
    case _ -> Root / "slow" =>
      Ok("Slow").async(TestScheduler.delayedStrategy(5.seconds))
  }

  val timeoutService = Timeout.apply(1.nanosecond)(myService)
  val fastReq = Request(GET, uri("/fast"))
  val slowReq = Request(GET, uri("/slow"))

  "Timeout Middleware" should {
    "Have no effect if the response is not delayed" in {
      timeoutService.apply(fastReq) must returnStatus (Status.Ok)
    }

    "return a 500 error if the result takes too long" in {
      timeoutService.apply(slowReq) must returnStatus (Status.InternalServerError)
    }

    "return the provided response if the result takes too long" in {
      val customTimeout = Response(Status.GatewayTimeout) // some people return 504 here.
      val altTimeoutService = Timeout(1.nanosecond, Task.now(customTimeout))(myService)
      altTimeoutService.apply(slowReq) must returnStatus (customTimeout.status)
    }

    "Handle infinite durations" in {
      val service = Timeout(Duration.Inf)(myService)
      service.apply(slowReq) must returnStatus (Status.Ok)
    }
  }

}
