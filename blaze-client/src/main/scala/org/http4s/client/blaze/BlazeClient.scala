package org.http4s
package client
package blaze

import org.http4s.blaze.pipeline.Command

import scala.concurrent.duration.Duration
import scalaz.concurrent.Task
import scalaz.{-\/, \/-}

/** Blaze client implementation */
object BlazeClient {
  def apply[A <: BlazeConnection](manager: ConnectionManager[A], idleTimeout: Duration, requestTimeout: Duration): Client = {
    Client(Service.lift { req =>
      val key = RequestKey.fromRequest(req)
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
            manager.invalidate(connection)
            manager.borrow(key).flatMap(loop(_, flushPrelude))

          case -\/(e) =>
            manager.invalidate(connection)
            Task.fail(e)
        }
      }
      val flushPrelude = !req.body.isHalt
      manager.borrow(key).flatMap(loop(_, flushPrelude))
    }, manager.shutdown())
  }
}

