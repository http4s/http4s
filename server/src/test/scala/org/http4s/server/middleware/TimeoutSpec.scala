package org.http4s
package server
package middleware

import java.util.concurrent.atomic.AtomicBoolean
import scala.concurrent.duration._
import org.http4s.dsl._
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.time.sleep

class TimeoutSpec extends Http4sSpec {

  val myService = HttpService {
    case req if req.uri.path == "/fast" =>
      Ok("Fast")
    case req if req.uri.path == "/slow" =>
      sleep(2.seconds).runLast.flatMap(_ => Ok("Slow"))
  }

  val timeoutService = Timeout.apply(500.millis)(myService)
  val fastReq = Request(GET, uri("/fast"))
  val slowReq = Request(GET, uri("/slow"))

  "Timeout Middleware" should {
    "have no effect if the response is not delayed" in {

      timeoutService.orNotFound(fastReq).unsafePerformSync.status must_== (Status.Ok)
    }

    "return a 500 error if the result takes too long" in {
      timeoutService.orNotFound(slowReq).unsafePerformSync.status must_== (Status.InternalServerError)
    }

    "return the provided response if the result takes too long" in {
      val customTimeout = Response(Status.GatewayTimeout) // some people return 504 here.
      val altTimeoutService = Timeout(1.millis, Task.now(customTimeout))(myService)
      altTimeoutService.orNotFound(slowReq).unsafePerformSync.status must_== (customTimeout.status)
    }

    "handle infinite durations" in {
      val service = Timeout(Duration.Inf)(myService)
      service.orNotFound(slowReq).unsafePerformSync.status must_== (Status.Ok)
    }

    "clean up resources of the loser" in {
      var clean = new AtomicBoolean(false)
      val service = HttpService {
        case _ =>
          (sleep(200.milliseconds) ++
            Process.eval(NoContent()) ++
            Process.eval_(Task.delay(clean.set(true))))
            .runLastOr(throw new AssertionError("Should have emitted NoContent"))
      }
      val timeoutService = Timeout(1.millis)(service)
      timeoutService.orNotFound(Request()).unsafePerformSync.status must_== (InternalServerError)
      // Give the losing response enough time to finish
      clean.get must beTrue.eventually
    }
  }
}
