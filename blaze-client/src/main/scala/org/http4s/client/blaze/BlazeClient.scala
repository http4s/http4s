package org.http4s
package client
package blaze

import cats.effect._
import cats.implicits._
import org.http4s.blaze.pipeline.Command
import org.log4s.getLogger

/** Blaze client implementation */
object BlazeClient {
  private[this] val logger = getLogger

  /** Construct a new [[Client]] using blaze components
    *
    * @param manager source for acquiring and releasing connections. Not owned by the returned client.
    * @param config blaze client configuration.
    * @param onShutdown arbitrary tasks that will be executed when this client is shutdown
    */
  def apply[F[_], A <: BlazeConnection[F]](manager: ConnectionManager[F, A],
                                           config: BlazeClientConfig,
                                           onShutdown: F[Unit])
                                          (implicit F: Sync[F]): Client[F] = {

    Client(Service.lift { req: Request[F] =>
      val key = RequestKey.fromRequest(req)

      // If we can't invalidate a connection, it shouldn't tank the subsequent operation,
      // but it should be noisy.
      def invalidate(connection: A): F[Unit] =
        manager
          .invalidate(connection)
          .handleError(e => logger.error(e)("Error invalidating connection"))

      def loop(next: manager.NextConnection): F[DisposableResponse[F]] = {
        // Add the timeout stage to the pipeline
        val ts = new ClientTimeoutStage(config.responseHeaderTimeout, config.idleTimeout, config.requestTimeout, bits.ClientTickWheel)
        next.connection.spliceBefore(ts)
        ts.initialize()

        next.connection.runRequest(req).attempt.flatMap {
          case Right(r)  =>
            val dispose = F.delay(ts.removeStage)
              .flatMap { _ => manager.release(next.connection) }
            F.pure(DisposableResponse(r, dispose))

          case Left(Command.EOF) =>
            invalidate(next.connection).flatMap { _ =>
              if (next.fresh) F.raiseError(new java.io.IOException(s"Failed to connect to endpoint: $key"))
              else {
                manager.borrow(key).flatMap { newConn =>
                  loop(newConn)
                }
              }
            }

          case Left(e) =>
            invalidate(next.connection) >> F.raiseError(e)
        }
      }
      manager.borrow(key).flatMap(loop)
    }, onShutdown)
  }
}

