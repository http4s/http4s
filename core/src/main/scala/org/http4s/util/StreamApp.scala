package org.http4s.util

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2.Stream
import fs2.async.mutable.Signal
import fs2.async
import org.log4s.getLogger

abstract class StreamApp[F[_]: Effect] {
  private[this] val logger = getLogger(classOf[StreamApp[F]])

  def stream(args: List[String]): Stream[F, Unit]

  //  private implicit val strategy: Strategy = Strategy.sequential
  // TODO: Not sure what this should be
  private implicit val executionContext = TrampolineExecutionContext

  private[this] val shutdownRequested: F[Signal[F, Boolean]] =
    async.signalOf[F, Boolean](false)

  final val requestShutdown =
    shutdownRequested.flatMap(_.set(true))

  /** Exposed for testing, so we can check exit values before the dramatic sys.exit */
  private[util] def doMain(args: Array[String]): Int = {
    val halted = async.signalOf[F, Boolean](false)

    val s =
      Stream.eval(shutdownRequested)
        .flatMap(_.interrupt(stream(args.toList)))
        .onFinalize(halted.flatMap(_.set(true)))

    sys.addShutdownHook {
      requestShutdown.runAsync(_ => IO.unit).unsafeRunAsync(_ => ())
      Stream.eval(halted).flatMap(_.discrete.takeWhile(_ == false)).run.runAsync(_ => IO.unit).unsafeRunSync()
    }

    s.run.runAsync(_ => IO.unit).attempt.unsafeRunSync() match {
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
