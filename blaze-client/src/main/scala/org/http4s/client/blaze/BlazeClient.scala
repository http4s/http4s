package org.http4s
package client
package blaze

import cats.effect._
import cats.effect.concurrent._
import cats.effect.implicits._
import cats.implicits._
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import org.http4s.blaze.pipeline.Command
import org.http4s.blaze.util.TickWheelExecutor
import org.http4s.blazecore.{IdleTimeoutStage, ResponseHeaderTimeoutStage}
import org.log4s.getLogger
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/** Blaze client implementation */
object BlazeClient {
  private[this] val logger = getLogger

  /** Construct a new [[Client]] using blaze components
    *
    * @param manager source for acquiring and releasing connections. Not owned by the returned client.
    * @param config blaze client configuration.
    * @param onShutdown arbitrary tasks that will be executed when this client is shutdown
    */
  @deprecated("Use BlazeClientBuilder", "0.19.0-M2")
  def apply[F[_], A <: BlazeConnection[F]](
      manager: ConnectionManager[F, A],
      config: BlazeClientConfig,
      onShutdown: F[Unit],
      ec: ExecutionContext)(implicit F: ConcurrentEffect[F]): Client[F] =
    makeClient(
      manager,
      responseHeaderTimeout = config.responseHeaderTimeout,
      idleTimeout = config.idleTimeout,
      requestTimeout = config.requestTimeout,
      scheduler = bits.ClientTickWheel,
      ec = ec
    )

  private[blaze] def makeClient[F[_], A <: BlazeConnection[F]](
      manager: ConnectionManager[F, A],
      responseHeaderTimeout: Duration,
      idleTimeout: Duration,
      requestTimeout: Duration,
      scheduler: TickWheelExecutor,
      ec: ExecutionContext
  )(implicit F: ConcurrentEffect[F]) =
    Client[F] { req =>
      Resource.suspend {
        val key = RequestKey.fromRequest(req)

        // If we can't invalidate a connection, it shouldn't tank the subsequent operation,
        // but it should be noisy.
        def invalidate(connection: A): F[Unit] =
          manager
            .invalidate(connection)
            .handleError(e => logger.error(e)("Error invalidating connection"))

        def borrow =
          Resource.makeCase(manager.borrow(key)) {
            case (_, ExitCase.Completed) =>
              F.unit
            case (next, ExitCase.Error(_) | ExitCase.Canceled) =>
              invalidate(next.connection)
          }

        def idleTimeoutStage(conn: A) =
          Resource.makeCase({
            idleTimeout match {
              case d: FiniteDuration =>
                val stage = new IdleTimeoutStage[ByteBuffer](d, scheduler, ec)
                F.delay(conn.spliceBefore(stage)).as(Some(stage))
              case _ =>
                F.pure(None)
            }
          }) {
            case (_, ExitCase.Completed) => F.unit
            case (stageOpt, _) => F.delay(stageOpt.foreach(_.removeStage()))
          }

        def loop: F[Resource[F, Response[F]]] =
          borrow.use { next =>
            idleTimeoutStage(next.connection).use { stageOpt =>
              val idleTimeoutF = stageOpt match {
                case Some(stage) => F.async[TimeoutException](stage.init)
                case None => F.never[TimeoutException]
              }
              val res = next.connection
                .runRequest(req, idleTimeoutF)
                .map { r =>
                  Resource.makeCase(F.pure(r)) {
                    case (_, ExitCase.Completed) =>
                      F.delay(stageOpt.foreach(_.removeStage()))
                        .guarantee(manager.release(next.connection))
                    case _ =>
                      F.delay(stageOpt.foreach(_.removeStage()))
                        .guarantee(manager.invalidate(next.connection))
                  }
                }
                .recoverWith {
                  case Command.EOF =>
                    invalidate(next.connection).flatMap { _ =>
                      if (next.fresh)
                        F.raiseError(
                          new java.net.ConnectException(s"Failed to connect to endpoint: $key"))
                      else {
                        loop
                      }
                    }
                }

              Deferred[F, Unit].flatMap { gate =>
                val responseHeaderTimeoutF: F[TimeoutException] =
                  F.delay {
                      val stage =
                        new ResponseHeaderTimeoutStage[ByteBuffer](
                          responseHeaderTimeout,
                          scheduler,
                          ec)
                      next.connection.spliceBefore(stage)
                      stage
                    }
                    .bracket(stage =>
                      F.asyncF[TimeoutException] { cb =>
                        F.delay(stage.init(cb)) >> gate.complete(())
                    })(stage => F.delay(stage.removeStage()))

                F.racePair(gate.get *> res, responseHeaderTimeoutF)
                  .flatMap[Resource[F, Response[F]]] {
                    case Left((r, fiber)) => fiber.cancel.as(r)
                    case Right((fiber, t)) => fiber.cancel >> F.raiseError(t)
                  }
              }
            }
          }

        val res = loop
        requestTimeout match {
          case d: FiniteDuration =>
            F.racePair(
                res,
                F.cancelable[TimeoutException] { cb =>
                  val c = scheduler.schedule(new Runnable {
                    def run() =
                      cb(Right(new TimeoutException(s"Request timeout after ${d.toMillis} ms")))
                  }, ec, d)
                  F.delay(c.cancel)
                }
              )
              .flatMap {
                case Left((r, fiber)) => fiber.cancel.as(r)
                case Right((fiber, t)) => fiber.cancel >> F.raiseError(t)
              }
          case _ =>
            res
        }
      }
    }
}
