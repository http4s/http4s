package org.http4s.util

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2._
import fs2.async.mutable.Signal
import org.log4s.getLogger
import scala.concurrent.ExecutionContext

abstract class StreamApp[F[_]](implicit F: Effect[F]) {
  private[this] val logger = getLogger(classOf[StreamApp[F]])

  def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, Nothing]

  private implicit val executionContext: ExecutionContext = TrampolineExecutionContext

  /** Adds a shutdown hook that interrupts the stream and waits for it to finish */
  private def addShutdownHook(
      requestShutdown: Signal[F, Boolean],
      halted: Signal[IO, Boolean]): F[Unit] =
    F.delay {
      sys.addShutdownHook {
        val hook = requestShutdown.set(true).runAsync(_ => IO.unit) >> halted.discrete
          .takeWhile(_ == false)
          .run
        hook.unsafeRunSync()
      }
      ()
    }

  /** Exposed for testing, so we can check exit values before the dramatic sys.exit */
  private[util] def doMain(args: List[String]): IO[Int] =
    async.ref[IO, Int].flatMap { exitCode =>
      async.signalOf[IO, Boolean](false).flatMap { halted =>
        async
          .signalOf[F, Boolean](false)
          .flatMap { requestShutdown =>
            addShutdownHook(requestShutdown, halted) >>
              stream(args, requestShutdown.set(true))
                .interruptWhen(requestShutdown)
                .run
          }
          .runAsync {
            case Left(t) =>
              IO(logger.error(t)("Error running stream")) >>
                halted.set(true) >>
                exitCode.setSyncPure(-1)
            case Right(_) =>
              halted.set(true) >>
                exitCode.setSyncPure(0)
          } >>
          exitCode.get
      }
    }

  def main(args: Array[String]): Unit =
    sys.exit(doMain(args.toList).unsafeRunSync)
}
