package org.http4s.util

import cats.effect.IO
import fs2.Stream
import fs2.async.signalOf
import org.log4s.getLogger

trait StreamApp {
  private[this] val logger = getLogger

  def stream(args: List[String]): Stream[IO, Unit]

  //  private implicit val strategy: Strategy = Strategy.sequential
  // TODO: Not sure what this should be
  private implicit val executionContext = TrampolineExecutionContext

  private[this] val shutdownRequested =
    signalOf[IO, Boolean](false).unsafeRunSync

  final val requestShutdown: IO[Unit] =
    shutdownRequested.set(true)

  /** Exposed for testing, so we can check exit values before the dramatic sys.exit */
  private[util] def doMain(args: Array[String]): Int = {
    val halted = signalOf[IO, Boolean](false).unsafeRunSync

    val p = shutdownRequested.interrupt(stream(args.toList))
      .onFinalize(halted.set(true))

    sys.addShutdownHook {
      requestShutdown.unsafeRunAsync(_ => ())
      halted.discrete.takeWhile(_ == false).run.unsafeRunSync
    }

    p.run.attempt.unsafeRunSync match {
      case Left(t) =>
        logger.error(t)("Error running stream")
        -1
      case Right(_) =>
        0
    }
  }

  final def main(args: Array[String]): Unit =
    sys.exit(doMain(args))
}
