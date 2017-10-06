package org.http4s
package util

import cats.effect.IO
import cats.implicits._
import fs2._
import fs2.Stream._
import fs2.async.mutable.Signal
import org.http4s.util.StreamApp.ExitCode
import scala.concurrent.duration._

class StreamAppSpec extends Http4sSpec {

  "StreamApp" should {

    /**
      * Simple Test Rig For Stream Apps
      * Takes the Stream that constitutes the Stream App
      * and observably cleans up when the process is stopped.
      */
    class TestStreamApp(stream: IO[Unit] => Stream[IO, ExitCode]) extends StreamApp[IO] {
      val cleanedUp: Signal[IO, Boolean] = async.signalOf[IO, Boolean](false).unsafeRunSync

      override def stream(args: List[String], requestShutdown: IO[Unit]): Stream[IO, ExitCode] =
        stream(requestShutdown).onFinalize(cleanedUp.set(true))
    }

    "Terminate server on a failed stream" in {
      val testApp = new TestStreamApp(_ => fail(new Throwable("Bad Initial Process")))
      testApp.doMain(List.empty) should returnValue(ExitCode.error)
      testApp.cleanedUp.get should returnValue(true)
    }

    "Terminate server on a valid stream" in {
      val testApp = new TestStreamApp(_ => emit(ExitCode.success))
      testApp.doMain(List.empty) should returnValue(ExitCode.success)
      testApp.cleanedUp.get should returnValue(true)
    }

    "Terminate server on an empty stream" in {
      val testApp = new TestStreamApp(_ => Stream.empty)
      testApp.doMain(List.empty) should returnValue(ExitCode.success)
      testApp.cleanedUp.get should returnValue(true)
    }

    "Terminate server with a specific exit code" in {
      val testApp = new TestStreamApp(_ => emit(ExitCode(42)))
      testApp.doMain(List.empty) should returnValue(ExitCode(42))
      testApp.cleanedUp.get should returnValue(true)
    }

    "requestShutdown shuts down a server from a separate thread" in {
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
      } yield (result, cleanedUp)).unsafeRunTimed(5.seconds) should beSome((ExitCode.success, true))
    }
  }
}
