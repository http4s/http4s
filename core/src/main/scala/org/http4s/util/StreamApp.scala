package org.http4s.util

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2._
import fs2.async.Ref
import fs2.async.mutable.Signal
import org.log4s.getLogger
import scala.concurrent.ExecutionContext

abstract class StreamApp[F[_]](implicit F: Effect[F]) {
  private[this] val logger = getLogger

  /** An application stream that should never emit or emit a single ExitCode */
  def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, ExitCode]

  /** Adds a shutdown hook that interrupts the stream and waits for it to finish */
  private def addShutdownHook(
      requestShutdown: Signal[F, Boolean],
      halted: Signal[IO, Boolean]): F[Unit] =
    F.delay {
      sys.addShutdownHook {
        val hook = requestShutdown.set(true).runAsync(_ => IO.unit) >>
          halted.discrete
            .takeWhile(_ == false)
            .run
        hook.unsafeRunSync()
      }
      ()
    }

  /** Exposed for testing, so we can check exit values before the dramatic sys.exit */
  private[util] def doMain(args: List[String]): IO[ExitCode] = {
    implicit val ec: ExecutionContext = execution.direct
    for {
      exitCodeRef <- async.ref[IO, ExitCode]
      halted <- async.signalOf[IO, Boolean](false)
      exitCode <- runStream(args, exitCodeRef, halted)
    } yield exitCode
  }

  /**
    * Runs the application stream to an ExitCode.
    *
    * @param args The command line arguments
    * @param exitCodeRef A ref that will be set to the exit code from the stream
    * @param halted A signal that is set when the application stream is done
    * @param ec Implicit EC to run the application stream
    * @return An IO that will produce an ExitCode
    */
  private[util] def runStream(
      args: List[String],
      exitCodeRef: Ref[IO, ExitCode],
      halted: Signal[IO, Boolean]
  )(implicit ec: ExecutionContext): IO[ExitCode] = {
    val runStreamLast: F[Option[ExitCode]] =
      for {
        requestShutdown <- async.signalOf[F, Boolean](false)
        _ <- addShutdownHook(requestShutdown, halted)
        exitCode <- stream(args, requestShutdown.set(true))
          .interruptWhen(requestShutdown)
          .take(1)
          .runLast
      } yield exitCode
    runStreamLast.runAsync {
      case Left(t) =>
        IO(logger.error(t)("Error running stream")) >>
          halted.set(true) >>
          exitCodeRef.setSyncPure(ExitCode.error)
      case Right(exitCode) =>
        halted.set(true) >>
          exitCodeRef.setSyncPure(exitCode.getOrElse(ExitCode.success))
    } >>
      exitCodeRef.get
  }

  def main(args: Array[String]): Unit =
    sys.exit(doMain(args.toList).unsafeRunSync.code.toInt)
}
