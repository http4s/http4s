package org.http4s
package server
package middleware

import scala.concurrent.duration._
import scalaz.concurrent.Task
import Method._

class TimeoutSpec extends Http4sSpec {

  val myService = HttpService {
    case req if req.uri.path == "/fast" => Response(Status.Ok).withBody("Fast")
    case req if req.uri.path == "/slow" => Task(Thread.sleep(10000)).flatMap(_ => Response(Status.Ok).withBody("Slow"))
  }

  val timeoutService = Timeout.apply(5.seconds)(myService)
  val fastReq = Request(GET, uri("/fast"))
  val slowReq = Request(GET, uri("/slow"))

  "Timeout Middleware" should {
    "Have no effect if the response is not delayed" in {

      timeoutService.apply(fastReq).run.status must_== (Status.Ok)
    }

    "return a 500 error if the result takes too long" in {
      timeoutService.apply(slowReq).run.status must_== (Status.InternalServerError)
    }

    "return the provided response if the result takes too long" in {
      val customTimeout = Response(Status.GatewayTimeout) // some people return 504 here.
      val altTimeoutService = Timeout(500.millis, Task.now(customTimeout))(myService)

      altTimeoutService.apply(slowReq).run.status must_== (customTimeout.status)
    }

    "Handle infinite durations" in {
      val service = Timeout(Duration.Inf)(myService)
      service.apply(slowReq).run.status must_== (Status.Ok)
    }
  }

}
