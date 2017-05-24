package org.http4s
package util

import scala.concurrent.duration._
import fs2.{Strategy, Stream, Task}
import fs2.Stream._
import fs2.async
import fs2.async.mutable.Signal

class StreamAppSpec extends Http4sSpec {

  "StreamApp" should {

    /**
      * Simple Test Rig For Process Apps
      * Takes the Process that constitutes the Process App
      * and observably cleans up when the process is stopped.
      *
      */
    class TestStreamApp(process: Stream[Task, Unit]) extends StreamApp {
      val cleanedUp : Signal[Task, Boolean] = async.signalOf(false)(Task.asyncInstance(testStrategy)).unsafeRun
      override def stream(args: List[String]): Stream[Task, Unit] = {
        process.onFinalize(cleanedUp.set(true))
      }
    }

    "Terminate Server on a Process Failure" in {
      val testApp = new TestStreamApp(
        fail(new Throwable("Bad Initial Process"))
      )
      testApp.doMain(Array.empty[String]) should_== -1
      testApp.cleanedUp.get.unsafeRun should_== true
    }

    "Terminate Server on a Valid Process" in {
      val testApp = new TestStreamApp(
        // emit one unit value
        emit("Valid Process").map(_ => ())
      )
      testApp.doMain(Array.empty[String]) should_== 0
      testApp.cleanedUp.get.unsafeRun should_== true
    }

    "requestShutdown Shuts Down a Server From A Separate Thread" in {
      val testApp = new TestStreamApp(
        // run forever, emit nothing
        eval_(Task.async[Nothing]{_ => })
      )
      (for {
        runApp <- Task.start(Task.delay(testApp.doMain(Array.empty[String])))
        _ <- testApp.requestShutdown
        exit <- runApp
        cleanedUp <- testApp.cleanedUp.get
      } yield (exit, cleanedUp)).unsafeTimed(5.seconds) should returnValue((0, true))
    }
  }
}
