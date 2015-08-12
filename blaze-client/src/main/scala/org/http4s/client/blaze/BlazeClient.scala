package org.http4s.client.blaze

import org.http4s.blaze.pipeline.Command
import org.http4s.client.Client
import org.http4s.{Request, Response}

import scalaz.concurrent.Task
import scalaz.stream.Process.eval_
import scalaz.{-\/, \/-}

/** Blaze client implementation */
final class BlazeClient(manager: ConnectionManager) extends Client {

  /** Shutdown this client, closing any open connections and freeing resources */
  override def shutdown(): Task[Unit] = manager.shutdown()

  override def prepare(req: Request): Task[Response] = {
    def tryClient(client: BlazeClientStage, freshClient: Boolean): Task[Response] = {
        client.runRequest(req).attempt.flatMap {
          case \/-(r)    =>
            val recycleProcess = eval_(Task.delay {
              if (!client.isClosed()) {
                manager.recycleClient(req, client)
              }
            })
            Task.now(r.copy(body = r.body ++ recycleProcess))

          case -\/(Command.EOF) if !freshClient =>
            manager.getClient(req, freshClient = true).flatMap(tryClient(_, true))

          case -\/(e) =>
            if (!client.isClosed()) {
              client.shutdown()
            }
            Task.fail(e)
        }
    }

    // TODO: Find a better strategy to deal with the potentially mutable body of the Request. Need to make sure the connection isn't stale.
    val requireFresh = !req.body.isHalt
    manager.getClient(req, freshClient = requireFresh).flatMap(tryClient(_, requireFresh))
  }
}
