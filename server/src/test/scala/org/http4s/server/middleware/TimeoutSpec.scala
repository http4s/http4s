package org.http4s
package server
package middleware

import org.specs2.time.NoTimeConversions

import scala.concurrent.duration._
import scalaz.concurrent.Task
import Method._

class TimeoutSpec extends Http4sSpec with NoTimeConversions {

  val myService = HttpService {
    case req if req.uri.path == "/fast" => Response(Status.Ok).withBody("Fast")
    case req if req.uri.path == "/slow" => Task(Thread.sleep(1000)).flatMap(_ => Response(Status.Ok).withBody("Slow"))
  }

  val timeoutService = Timeout.apply(500.millis)(myService)

  "Timeout Middleware" should {
    "Have no effect if the response is not delayed" in {
      val req = Request(GET, uri("/fast"))

      timeoutService.apply(req).run.status must equal (Status.Ok)
    }

    "return a 500 error if the result takes too long" in {
      val req = Request(GET, uri("/slow"))

      timeoutService.apply(req).run.status must equal (Status.InternalServerError)
    }

    "return the provided response if the result takes too long" in {
      val req = Request(GET, uri("/slow"))
      val customTimeout = Response(Status.GatewayTimeout) // some people return 504 here.
      val altTimeoutService = Timeout(500.millis, Task.now(customTimeout))(myService)

      altTimeoutService(req).run.status must equal (customTimeout.status)
    }

    "Handle infinite durations" in {
      val service = Timeout(Duration.Inf)(myService)
      
      service(Request(GET, uri("/slow"))).run.status must equal(Status.Ok)
    }
  }

}
