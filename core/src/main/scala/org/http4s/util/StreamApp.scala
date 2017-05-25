package org.http4s.util

import fs2.{Strategy, Stream, Task}
import fs2.Stream.{eval, eval_}
import fs2.async.signalOf
import org.log4s.getLogger

trait StreamApp {
  private[this] val logger = getLogger

  def stream(args: List[String]): Stream[Task, Nothing]

  private implicit val strategy: Strategy = Strategy.sequential

  private[this] val shutdownRequested =
    signalOf[Task, Boolean](false).unsafeRun

  final val requestShutdown: Task[Unit] =
    shutdownRequested.set(true)

  /** Exposed for testing, so we can check exit values before the dramatic sys.exit */
  private[util] def doMain(args: Array[String]): Int = {
    val halted = signalOf[Task, Boolean](false).unsafeRun

    val p = shutdownRequested.interrupt(stream(args.toList))
      .onFinalize(halted.set(true))

    sys.addShutdownHook {
      requestShutdown.unsafeRunAsync(_ => ())
      halted.discrete.takeWhile(_ == false).run.unsafeRun
    }

    p.run.attempt.unsafeRun match {
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
