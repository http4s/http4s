package org.http4s
package client
package blaze


import org.http4s.blaze.pipeline.Command
import org.log4s.getLogger

import scalaz.concurrent.Task
import scalaz.{-\/, \/-}

/** Blaze client implementation */
object BlazeClient {
  private[this] val logger = getLogger

  /** Construct a new [[Client]] using blaze components
    *
    * @param manager source for acquiring and releasing connections. Not owned by the returned client.
    * @param config blaze client configuration.
    * @param onShutdown arbitrary tasks that will be executed when this client is shutdown
    */
  def apply[A <: BlazeConnection](manager: ConnectionManager[A],
                                  config: BlazeClientConfig,
                                  onShutdown: Task[Unit]): Client = {

    Client(Service.lift { req =>
      val key = RequestKey.fromRequest(req)

      // If we can't invalidate a connection, it shouldn't tank the subsequent operation,
      // but it should be noisy.
      def invalidate(connection: A): Task[Unit] =
        manager.invalidate(connection).handle {
          case e => logger.error(e)("Error invalidating connection")
        }

      def loop(next: manager.NextConnection): Task[DisposableResponse] = {
        // Add the timeout stage to the pipeline
        val ts = new ClientTimeoutStage(config.responseHeaderTimeout, config.idleTimeout, config.requestTimeout, bits.ClientTickWheel)
        next.connection.spliceBefore(ts)
        ts.initialize()

        next.connection.runRequest(req).attempt.flatMap {
          case \/-(r)  =>
            val dispose = Task.delay(ts.removeStage)
              .flatMap { _ => manager.release(next.connection) }
            Task.now(DisposableResponse(r, dispose))

          case -\/(Command.EOF) =>
            invalidate(next.connection).flatMap { _ =>
              if (next.fresh) Task.fail(new java.io.IOException(s"Failed to connect to endpoint: $key"))
              else {
                manager.borrow(key).flatMap { newConn =>
                  loop(newConn)
                }
              }
            }

          case -\/(e) =>
            invalidate(next.connection).flatMap { _ =>
              Task.fail(e)
            }
        }
      }
      manager.borrow(key).flatMap(loop)
    }, onShutdown)
  }
}

