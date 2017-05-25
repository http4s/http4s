package org.http4s
package util

import cats.effect.IO
import fs2.Stream._
import fs2.{Stream, async}
import fs2.async.mutable.Signal

import scala.concurrent.duration._

class StreamAppSpec extends Http4sSpec {

  "StreamApp" should {

    /**
      * Simple Test Rig For Process Apps
      * Takes the Process that constitutes the Process App
      * and observably cleans up when the process is stopped.
      */
    class TestStreamApp(process: Stream[IO, Unit]) extends StreamApp {
      val cleanedUp : Signal[IO, Boolean] = async.signalOf[IO, Boolean](false).unsafeRunSync
      override def stream(args: List[String]): Stream[IO, Unit] = {
        process.onFinalize(cleanedUp.set(true))
      }
    }

    "Terminate Server on a Process Failure" in {
      val testApp = new TestStreamApp(
        fail(new Throwable("Bad Initial Process"))
      )
      testApp.doMain(Array.empty[String]) should_== -1
      testApp.cleanedUp.get.unsafeRunSync should_== true
    }

    "Terminate Server on a Valid Process" in {
      val testApp = new TestStreamApp(
        // emit one unit value
        emit("Valid Process").map(_ => ())
      )
      testApp.doMain(Array.empty[String]) should_== 0
      testApp.cleanedUp.get.unsafeRunSync should_== true
    }

    "requestShutdown Shuts Down a Server From A Separate Thread" in {
      val testApp = new TestStreamApp(
        // run forever, emit nothing
        eval_(IO.async[Nothing]{_ => })
      )
      (for {
        runApp <- async.start(IO(testApp.doMain(Array.empty[String])))
        _ <- testApp.requestShutdown
        exit <- runApp
        cleanedUp <- testApp.cleanedUp.get
      } yield (exit, cleanedUp)).unsafeRunTimed(5.seconds) should beSome((0, true))
    }
  }
}
