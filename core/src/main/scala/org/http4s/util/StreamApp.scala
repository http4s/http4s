package org.http4s.util

import fs2.{Strategy, Stream, Task}
import fs2.Stream.{eval, eval_}
import fs2.async.signalOf
import org.log4s.getLogger

trait StreamApp {
  private[this] val logger = getLogger

  def main(args: List[String]): Stream[Task, Unit]

  private implicit val strategy: Strategy = Strategy.sequential

  private[this] val shutdownRequested =
    signalOf[Task, Boolean](false).unsafeRun

  final val requestShutdown: Task[Unit] =
    shutdownRequested.set(true)

  final def main(args: Array[String]): Unit = {
    val halted = signalOf[Task, Boolean](false).unsafeRun

    val p = shutdownRequested.interrupt(main(args.toList))
      .append(eval_(halted.set(true)))

    sys.addShutdownHook {
      requestShutdown.unsafeRunAsync(_ => ())
      halted.discrete.takeWhile(_ == false).run.unsafeRun
    }

    p.run.attempt.unsafeRun match {
      case Left(t) =>
        logger.error(t)("Error running stream")
        System.exit(-1)
      case Right(_) =>
        ()
    }
  }
}
