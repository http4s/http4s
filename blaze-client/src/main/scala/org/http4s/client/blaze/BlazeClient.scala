package org.http4s
package client
package blaze

import cats.effect._
import cats.implicits._
import java.nio.ByteBuffer
import java.util.concurrent.{TimeUnit, TimeoutException}
import org.http4s.blaze.pipeline.Command
import org.http4s.blazecore.{IdleTimeoutStage, ResponseHeaderTimeoutStage}
import org.log4s.getLogger
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
      onShutdown: F[Unit])(implicit F: Concurrent[F], clock: Clock[F]): Client[F] =
    makeClient(
      manager,
      responseHeaderTimeout = config.responseHeaderTimeout,
      idleTimeout = config.idleTimeout,
      requestTimeout = config.requestTimeout
    )

  private[blaze] def makeClient[F[_], A <: BlazeConnection[F]](
      manager: ConnectionManager[F, A],
      responseHeaderTimeout: Duration,
      idleTimeout: Duration,
      requestTimeout: Duration
  )(implicit F: Concurrent[F], clock: Clock[F]) =
    Client[F] { req =>
      Resource.suspend {
        val key = RequestKey.fromRequest(req)

        // If we can't invalidate a connection, it shouldn't tank the subsequent operation,
        // but it should be noisy.
        def invalidate(connection: A): F[Unit] =
          manager
            .invalidate(connection)
            .handleError(e => logger.error(e)("Error invalidating connection"))

        def loop(next: manager.NextConnection, submitTime: Long): F[Resource[F, Response[F]]] = {
          // Add the timeout stage to the pipeline
          val res: F[Resource[F, Response[F]]] = clock.monotonic(TimeUnit.NANOSECONDS).flatMap {
            now =>
              val elapsed = (now - submitTime).nanos
              val ts = new ClientTimeoutStage(
                if (elapsed > requestTimeout) Duration.Zero else requestTimeout - elapsed,
                bits.ClientTickWheel
              )
              next.connection.spliceBefore(ts)
              ts.initialize()

              val idleTimeoutF = F.cancelable[TimeoutException] { cb =>
                val stage = new IdleTimeoutStage[ByteBuffer](idleTimeout, cb, bits.ClientTickWheel)
                next.connection.spliceBefore(stage)
                stage.stageStartup()
                F.delay(stage.removeStage)
              }

              next.connection.runRequest(req, idleTimeoutF).attempt.flatMap {
                case Right(r) =>
                  val dispose = F
                    .delay(ts.removeStage)
                    .flatMap { _ =>
                      manager.release(next.connection)
                    }
                  F.pure(Resource(F.pure(r -> dispose)))

                case Left(Command.EOF) =>
                  invalidate(next.connection).flatMap { _ =>
                    if (next.fresh)
                      F.raiseError(
                        new java.net.ConnectException(s"Failed to connect to endpoint: $key"))
                    else {
                      manager.borrow(key).flatMap { newConn =>
                        loop(newConn, submitTime)
                      }
                    }
                  }

                case Left(e) =>
                  invalidate(next.connection) *> F.raiseError(e)
              }
          }

          val responseHeaderTimeoutF = F.cancelable[TimeoutException] { cb =>
            val stage = new ResponseHeaderTimeoutStage[ByteBuffer](
              responseHeaderTimeout,
              cb,
              bits.ClientTickWheel)
            next.connection.spliceBefore(stage)
            stage.stageStartup()
            F.delay(stage.removeStage)
          }

          F.racePair(res, responseHeaderTimeoutF).flatMap {
            case Left((r, fiber)) => fiber.cancel.as(r)
            case Right((fiber, t)) => fiber.cancel >> F.raiseError(t)
          }
        }

        clock.monotonic(TimeUnit.NANOSECONDS).flatMap { submitTime =>
          manager.borrow(key).flatMap(loop(_, submitTime))
        }
      }
    }
}
