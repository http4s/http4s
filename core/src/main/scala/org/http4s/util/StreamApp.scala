package org.http4s.util

import cats.effect._
import cats.effect.implicits._
import fs2.Stream._
import fs2._
import fs2.async._
import org.log4s.getLogger

abstract class StreamApp[F[_]](implicit F: Effect[F]) {
  private[this] val logger = getLogger(classOf[StreamApp[F]])

  def stream(args: List[String], requestShutdown: F[Unit]): Stream[F, Nothing]

  private implicit val executionContext = TrampolineExecutionContext

  /** Exposed for testing, so we can check exit values before the dramatic sys.exit */
  private[util] def mainStream(args: Array[String]): Stream[F, Nothing] = {
    val s = for {
      shutdownRequested <- eval(signalOf[F, Boolean](false))
      halted            <- eval(signalOf[F, Boolean](false))
      _ <- eval(F.delay(sys.addShutdownHook {
        unsafeRunAsync(shutdownRequested.set(true))(_ => IO.unit)
        halted.discrete.takeWhile(_ == false).run.runAsync(_ => IO.unit).unsafeRunSync()
      }))
      res <- stream(args.toList, shutdownRequested.set(true))
        .interruptWhen(shutdownRequested)
        .onFinalize(halted.set(true))
        .as(())
    } yield res
    s.drain
  }

  final def main(args: Array[String]): Unit =
    mainStream(args).run
      .runAsync {
        case Left(t) =>
          IO {
            logger.error(t)("Error running stream")
            sys.exit(-1)
          }
        case Right(()) =>
          IO(sys.exit(0))
      }
      .unsafeRunSync()

}
