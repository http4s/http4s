package org.http4s
package client
package blaze

import cats.effect._
import cats.effect.concurrent.Deferred
import cats.implicits._
import java.util.concurrent.TimeUnit
import org.http4s.blaze.pipeline.Command
import org.http4s.blaze.util.TickWheelExecutor
import org.log4s.getLogger
import scala.concurrent.duration._
import scala.concurrent.TimeoutException

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
      onShutdown: F[Unit])(implicit F: ConcurrentEffect[F], clock: Clock[F]): Client[F] =
    makeClient(
      manager,
      responseHeaderTimeout = config.responseHeaderTimeout,
      idleTimeout = config.idleTimeout,
      requestTimeout = config.requestTimeout,
      bits.ClientTickWheel
    )

  private[blaze] def makeClient[F[_], A <: BlazeConnection[F]](
      manager: ConnectionManager[F, A],
      responseHeaderTimeout: Duration,
      idleTimeout: Duration,
      requestTimeout: Duration,
      scheduler: TickWheelExecutor
  )(implicit F: ConcurrentEffect[F], clock: Clock[F]) =
    Client[F] { req =>
      Resource.suspend {
        val key = RequestKey.fromRequest(req)

        // If we can't invalidate a connection, it shouldn't tank the subsequent operation,
        // but it should be noisy.
        def invalidate(connection: A): F[Unit] =
          manager
            .invalidate(connection)
            .handleError(e => logger.error(e)("Error invalidating connection"))

        def loop(
            next: manager.NextConnection,
            submitTime: Long,
            timedOut: Deferred[F, TimeoutException]): F[Resource[F, Response[F]]] =
          // Add the timeout stage to the pipeline
          clock.monotonic(TimeUnit.NANOSECONDS).flatMap { now =>
            val elapsed = (now - submitTime).nanos

            val ts = new ClientTimeoutStage(
              if (elapsed > responseHeaderTimeout) Duration.Zero
              else responseHeaderTimeout - elapsed,
              idleTimeout,
              if (elapsed > requestTimeout) Duration.Zero else requestTimeout - elapsed,
              scheduler,
              timedOut
            )
            next.connection.spliceBefore(ts)
            ts.initialize()

            next.connection.runRequest(req).attempt.flatMap {
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
                      loop(newConn, submitTime, timedOut)
                    }
                  }
                }

              case Left(e) =>
                invalidate(next.connection) *> F.raiseError(e)
            }
          }

        clock.monotonic(TimeUnit.NANOSECONDS).flatMap { submitTime =>
          Deferred[F, TimeoutException].flatMap { timedOut =>
            F.racePair(manager.borrow(key).flatMap(loop(_, submitTime, timedOut)), timedOut.get)
              .flatMap {
                case Left((r, fiber)) => fiber.cancel.as(r)
                case Right((fiber, t)) => fiber.cancel >> F.raiseError(t)
              }
          }
        }
      }
    }
}
