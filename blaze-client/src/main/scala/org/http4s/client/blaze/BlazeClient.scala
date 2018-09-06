package org.http4s
package client
package blaze

import java.time.Instant

import cats.data.Kleisli
import cats.effect._
import cats.implicits._
import org.http4s.blaze.pipeline.Command
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
      onShutdown: F[Unit])(implicit F: Sync[F]): Client[F] =
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
  )(implicit F: Sync[F]) =
    Client[F](
      Kleisli { req =>
        F.suspend {
          val key = RequestKey.fromRequest(req)
          val submitTime = Instant.now()

          // If we can't invalidate a connection, it shouldn't tank the subsequent operation,
          // but it should be noisy.
          def invalidate(connection: A): F[Unit] =
            manager
              .invalidate(connection)
              .handleError(e => logger.error(e)("Error invalidating connection"))

          def loop(next: manager.NextConnection): F[DisposableResponse[F]] = {
            // Add the timeout stage to the pipeline
            val elapsed = (Instant.now.toEpochMilli - submitTime.toEpochMilli).millis
            val ts = new ClientTimeoutStage(
              if (elapsed > responseHeaderTimeout) 0.milli
              else responseHeaderTimeout - elapsed,
              idleTimeout,
              if (elapsed > requestTimeout) 0.milli else requestTimeout - elapsed,
              bits.ClientTickWheel
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
                F.pure(DisposableResponse(r, dispose))

              case Left(Command.EOF) =>
                invalidate(next.connection).flatMap { _ =>
                  if (next.fresh)
                    F.raiseError(
                      new java.net.ConnectException(s"Failed to connect to endpoint: $key"))
                  else {
                    manager.borrow(key).flatMap { newConn =>
                      loop(newConn)
                    }
                  }
                }

              case Left(e) =>
                invalidate(next.connection) *> F.raiseError(e)
            }
          }
          manager.borrow(key).flatMap(loop)
        }
      },
      manager.shutdown
    )
}
