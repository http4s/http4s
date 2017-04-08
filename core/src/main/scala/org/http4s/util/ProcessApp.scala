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

  def main(args: List[String]): Process[Task, Unit]

  private[this] val shutdownRequested =
    async.signalOf(false)

  final val requestShutdown: Task[Unit] =
    shutdownRequested.set(true)

  final def main(args: Array[String]): Unit = {
    val halted = async.signalOf(false)

    val p = (shutdownRequested.discrete wye main(args.toList))(wye.interrupt)
      .append(eval_(halted set true))

    sys.addShutdownHook {
      requestShutdown.unsafePerformAsync(_ => ())
      halted.discrete.takeWhile(_ == false).run.unsafePerformSync
    }

    p.run.unsafePerformSyncAttempt match {
      case -\/(t) =>
        logger.error(t)("Error running process")
        System.exit(-1)
      case \/-(_) =>
        ()
    }
  }
}
