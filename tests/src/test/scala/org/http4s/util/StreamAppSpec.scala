package org.http4s
package util

import cats.effect.IO
import cats.implicits._
import fs2._
import fs2.Stream._
import fs2.async.mutable.Signal
import scala.concurrent.duration._

class StreamAppSpec extends Http4sSpec {

  "StreamApp" should {

    /**
      * Simple Test Rig For Stream Apps
      * Takes the Stream that constitutes the Stream App
      * and observably cleans up when the process is stopped.
      */
    class TestStreamApp(stream: IO[Unit] => Stream[IO, Nothing]) extends StreamApp[IO] {
      val cleanedUp: Signal[IO, Boolean] = async.signalOf[IO, Boolean](false).unsafeRunSync

      override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, Nothing] =
        stream(requestShutdown).onFinalize(cleanedUp.set(true))
    }

    "Terminate Server on a Stream Failure" in {
      val testApp = new TestStreamApp(_ => fail(new Throwable("Bad Initial Process")))
      testApp.doMain(List.empty) should returnValue(-1)
      testApp.cleanedUp.get.unsafeRunSync should beTrue
    }

    "Terminate Server on a Valid Process" in {
      val testApp = new TestStreamApp(_ => emit("Valid Process").drain)
      testApp.doMain(List.empty) should returnValue(0)
      testApp.cleanedUp.get.unsafeRunSync should beTrue
    }

    "requestShutdown Shuts Down a Server From A Separate Thread" in {
      val requestShutdown = async.signalOf[IO, IO[Unit]](IO.unit).unsafeRunSync

      val testApp = new TestStreamApp(
        shutdown =>
          eval(requestShutdown.set(shutdown)) >>
            // run forever, emit nothing
            eval_(IO.async[Nothing] { _ =>
              }))

      (for {
        runApp <- async.start(testApp.doMain(List.empty))
        // Wait for app to start
        _ <- requestShutdown.discrete.takeWhile(_ == IO.unit).run
        // Run shutdown task
        _ <- requestShutdown.get.flatten
        result <- runApp
        cleanedUp <- testApp.cleanedUp.get
      } yield (result, cleanedUp)).unsafeRunTimed(5.seconds) should beSome((0, true))
    }
  }
}
