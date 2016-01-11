package org.http4s
package client
package blaze

import org.http4s.blaze.pipeline.Command

import scala.concurrent.duration.Duration
import scalaz.concurrent.Task
import scalaz.{-\/, \/-}

/** Blaze client implementation */
object BlazeClient {
  def apply(manager: ConnectionManager, idleTimeout: Duration, requestTimeout: Duration): Client = {
    Client(Service.lift { req =>
      val key = RequestKey.fromRequest(req)
      def tryClient(client: BlazeClientStage, flushPrelude: Boolean): Task[DisposableResponse] = {
        // Add the timeout stage to the pipeline
        val ts = new ClientTimeoutStage(idleTimeout, requestTimeout, bits.ClientTickWheel)
        client.spliceBefore(ts)
        ts.initialize()

        client.runRequest(req, flushPrelude).attempt.flatMap {
          case \/-(r)  =>
            val dispose = Task.delay(ts.removeStage)
              .flatMap { _ => manager.release(client) }
            Task.now(DisposableResponse(r, dispose))

          case -\/(Command.EOF) =>
            manager.dispose(client)
            manager.borrow(key).flatMap(tryClient(_, flushPrelude))

          case -\/(e) =>
            manager.dispose(client)
            Task.fail(e)
        }
      }
      val flushPrelude = !req.body.isHalt
      manager.borrow(key).flatMap(tryClient(_, flushPrelude))
    }, manager.shutdown())
  }
}

