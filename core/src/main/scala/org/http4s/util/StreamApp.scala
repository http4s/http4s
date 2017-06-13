package org.http4s.util

import cats.effect._
import cats.effect.implicits._
import fs2._
import fs2.async._
import fs2.async.mutable.Signal
import org.log4s.getLogger

import scala.concurrent.SyncVar

abstract class StreamApp[F[_]](implicit F: Effect[F]) {
  private[this] val logger = getLogger(classOf[StreamApp[F]])

  def stream(args: List[String]): Stream[F, Nothing]

  // private implicit val strategy: Strategy = Strategy.sequential
  // TODO: Not sure what this should be
  private implicit val executionContext = TrampolineExecutionContext

  private[this] val shutdownRequested: Signal[F, Boolean] = {
    val signal = new SyncVar[Either[Throwable, Signal[F, Boolean]]]
    unsafeRunAsync(signalOf[F, Boolean](false)) { a =>
      signal.put(a)
      IO.unit
    }
    signal.get.fold(throw _, identity)
  }

  final val requestShutdown =
    shutdownRequested.set(true)

  /** Exposed for testing, so we can check exit values before the dramatic sys.exit */
  private[util] def doMain(args: Array[String]): Int = {
    val s = Stream
      .eval(signalOf[F, Boolean](false))
      .flatMap { halted =>
        val s = shutdownRequested
          .interrupt(stream(args.toList))
          .onFinalize(halted.set(true))

        Stream
          .eval(F.delay {
            sys.addShutdownHook {
              unsafeRunAsync(requestShutdown)(_ => IO.unit)
              halted.discrete.takeWhile(_ == false).run.runAsync(_ => IO.unit).unsafeRunSync()
            }
          })
          .flatMap(_ => s)
      }
      .run

    val exit = new SyncVar[Int]
    unsafeRunAsync(s) {
      case Left(t) =>
        logger.error(t)("Error running stream")
        IO.pure(exit.put(-1))
      case Right(_) =>
        IO.pure(exit.put(0))
    }
    exit.get
  }

  final def main(args: Array[String]): Unit =
    sys.exit(doMain(args))
}
