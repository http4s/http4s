package org.http4s.util

import fs2._
import fs2.async._
import fs2.async.mutable.Signal
import fs2.util.Attempt
import org.log4s.getLogger

import scala.concurrent.SyncVar

abstract class StreamApp {
  private[this] val logger = getLogger(classOf[StreamApp])

  def stream(args: List[String]): Stream[Task, Nothing]

  private implicit val strategy: Strategy = Strategy.sequential

  private[this] val shutdownRequested: Signal[Task, Boolean] = {
    val signal = new SyncVar[Attempt[Signal[Task, Boolean]]]
    signalOf[Task, Boolean](false).unsafeRunAsync { a =>
      signal.put(a)
    }
    signal.get.fold(throw _, identity)
  }

  final val requestShutdown =
    shutdownRequested.set(true)

  /** Exposed for testing, so we can check exit values before the dramatic sys.exit */
  private[util] def doMain(args: Array[String]): Int = {
    val s = Stream
      .eval(signalOf[Task, Boolean](false))
      .flatMap { halted =>
        val s = shutdownRequested
          .interrupt(stream(args.toList))
          .onFinalize(halted.set(true))

        Stream
          .eval(Task.delay {
            sys.addShutdownHook {
              requestShutdown.unsafeRunAsync { _ => }
              halted.discrete.takeWhile(_ == false).run.unsafeRunSync()
            }
          })
          .flatMap(_ => s)
      }
      .run

    val exit = new SyncVar[Int]
    s.unsafeRunAsync {
      case Left(t) =>
        logger.error(t)("Error running stream")
        exit.put(-1)
      case Right(_) =>
        exit.put(0)
    }
    exit.get
  }

  final def main(args: Array[String]): Unit =
    sys.exit(doMain(args))
}
