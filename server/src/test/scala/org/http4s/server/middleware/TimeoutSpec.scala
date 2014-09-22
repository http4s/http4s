package org.http4s
package server
package middleware

import org.specs2.time.NoTimeConversions

import scala.concurrent.duration._
import scalaz.concurrent.Task
import Method._

class TimeoutSpec extends Http4sSpec with NoTimeConversions {

  val myservice: Service = {
    case req if req.uri.path == "/fast" => ResponseBuilder(Status.Ok, "Fast")
    case req if req.uri.path == "/slow" => Task(Thread.sleep(1000)).flatMap(_ => ResponseBuilder(Status.Ok, "Slow"))
  }

  val timeoutService = Timeout.apply(500.millis)(myservice)

  "Timeout Middleware" should {
    "Have no effect if the response is not delayed" in {
      val req = Request(GET, uri("/fast"))

      timeoutService.apply(req).run.status must_==(Status.Ok)
    }

    "return a timeout if the result takes too long" in {
      val req = Request(GET, uri("/slow"))

      timeoutService.apply(req).run.status must_==(Status.RequestTimeout)
    }

    "Handle infinite durations" in {
      val service = Timeout(Duration.Inf)(myservice)

      service(Request(GET, uri("/slow"))).run.status must_==(Status.Ok)
    }
  }

}
