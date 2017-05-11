package org.http4s.util

import org.http4s.internal.compatibility._
import org.log4s.getLogger
import scalaz.{-\/, \/-}
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.Process.eval_
import scalaz.stream.{async, wye}

trait ProcessApp {
  private[this] val logger = org.log4s.getLogger

  def process(args: List[String]): Process[Task, Nothing]

  private[this] val shutdownRequested =
    async.signalOf(false)

  final val requestShutdown: Task[Unit] =
    shutdownRequested.set(true)

  /** Exposed for testing, so we can check exit values before the dramatic sys.exit */
  private[util] def doMain(args: Array[String]): Int = {
    val halted = async.signalOf(false)

    val p = (shutdownRequested.discrete wye process(args.toList))(wye.interrupt)
      .onComplete(eval_(halted set true))

    sys.addShutdownHook {
      requestShutdown.unsafePerformAsync(_ => ())
      halted.discrete.takeWhile(_ == false).run.unsafePerformSync
    }

    p.run.attempt.unsafePerformSync match {
      case -\/(t) =>
        logger.error(t)("Error running process")
        -1
      case \/-(a) =>
        0
    }
  }

  final def main(args: Array[String]): Unit =
    sys.exit(doMain(args))
}
