package org.http4s
package client
package blaze

import org.http4s.blaze.pipeline.Command
import org.log4s.getLogger

import scala.concurrent.duration.Duration
import scalaz.concurrent.Task
import scalaz.{-\/, \/-}

/** Blaze client implementation */
object BlazeClient {
  private[this] val logger = getLogger

  def apply[A <: BlazeConnection](manager: ConnectionManager[A], idleTimeout: Duration, requestTimeout: Duration): Client = {
    Client(Service.lift { req =>
      val key = RequestKey.fromRequest(req)

      // If we can't invalidate a connection, it shouldn't tank the subsequent operation,
      // but it should be noisy.
      def invalidate(connection: A): Task[Unit] =
        manager.invalidate(connection).handle {
          case e => logger.error(e)("Error invalidating connection")
        }

      def loop(connection: A, flushPrelude: Boolean): Task[DisposableResponse] = {
        // Add the timeout stage to the pipeline
        val ts = new ClientTimeoutStage(idleTimeout, requestTimeout, bits.ClientTickWheel)
        connection.spliceBefore(ts)
        ts.initialize()

        connection.runRequest(req, flushPrelude).attempt.flatMap {
          case \/-(r)  =>
            val dispose = Task.delay(ts.removeStage)
              .flatMap { _ => manager.release(connection) }
            Task.now(DisposableResponse(r, dispose))

          case -\/(Command.EOF) =>
            invalidate(connection).flatMap { _ =>
              manager.borrow(key).flatMap { newConn =>
                loop(newConn, flushPrelude)
              }
            }

          case -\/(e) =>
            invalidate(connection).flatMap { _ =>
              Task.fail(e)
            }
        }
      }
      val flushPrelude = !req.body.isHalt
      manager.borrow(key).flatMap(loop(_, flushPrelude))
    }, manager.shutdown())
  }
}

