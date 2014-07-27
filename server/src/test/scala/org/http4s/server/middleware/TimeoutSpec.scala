package org.http4s
package server
package middleware

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions

import scala.concurrent.duration._
import scalaz.concurrent.Task

class TimeoutSpec extends Specification with NoTimeConversions {

  val myservice: HttpService = {
    case req if req.requestUri.path == "/fast" => Status.Ok("Fast")
    case req if req.requestUri.path == "/slow" => Task(Thread.sleep(1000)).flatMap(_ => Status.Ok("Slow"))
  }

  val timeoutService = Timeout.apply(500.millis)(myservice)

  "Timeout Middleware" should {
    "Have no effect if the response is not delayed" in {
      val req = Method.Get("/fast").run

      timeoutService.apply(req).run.status must_==(Status.Ok)
    }

    "return a timeout if the result takes too long" in {
      val req = Method.Get("/slow").run

      timeoutService.apply(req).run.status must_==(Status.RequestTimeOut)
    }

    "Handle infinite durations" in {
      val service = Timeout(Duration.Inf)(myservice)

      service(Method.Get("/slow").run).run.status must_==(Status.Ok)
    }
  }

}
