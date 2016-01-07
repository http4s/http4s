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
      def tryClient(client: BlazeClientStage, freshClient: Boolean, flushPrelude: Boolean): Task[DisposableResponse] = {
        // Add the timeout stage to the pipeline
        val ts = new ClientTimeoutStage(idleTimeout, requestTimeout, bits.ClientTickWheel)
        client.spliceBefore(ts)
        ts.initialize()

        client.runRequest(req, flushPrelude).attempt.flatMap {
          case \/-(r)    =>
            val dispose = Task.delay {
              if (!client.isClosed()) {
                ts.removeStage
                manager.recycleClient(req, client)
              }
            }
            Task.now(DisposableResponse(r, dispose))

          case -\/(Command.EOF) if !freshClient =>
            manager.getClient(req, freshClient = true).flatMap(tryClient(_, true, flushPrelude))

          case -\/(e) =>
            if (!client.isClosed()) client.shutdown()
            Task.fail(e)
        }
      }

      val flushPrelude = !req.body.isHalt
      manager.getClient(req, false).flatMap(tryClient(_, false, flushPrelude))
    }, manager.shutdown())
  }
}

