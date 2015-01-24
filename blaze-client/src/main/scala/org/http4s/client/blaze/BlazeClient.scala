package org.http4s.client.blaze

import org.http4s.blaze.pipeline.Command
import org.http4s.client.Client
import org.http4s.{Request, Response}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}
import scalaz.concurrent.Task
import scalaz.stream.Process.eval_
import scalaz.{-\/, \/-}

/** Base on which to implement a BlazeClient */
final class BlazeClient(manager: ConnectionManager, ec: ExecutionContext) extends Client {

  /** Shutdown this client, closing any open connections and freeing resources */
  override def shutdown(): Task[Unit] = manager.shutdown()

  override def prepare(req: Request): Task[Response] = Task.async { cb =>
    def tryClient(client: Try[BlazeClientStage], retries: Int): Unit = client match {
      case Success(client) =>
        client.runRequest(req).runAsync {
          case \/-(r)    =>
            val recycleProcess = eval_(Task.delay {
              if (!client.isClosed()) {
                manager.recycleClient(req, client)
              }
            })

            cb(\/-(r.copy(body = r.body ++ recycleProcess)))

          case -\/(Command.EOF) if retries > 0 =>
            manager.getClient(req, fresh = true).onComplete(tryClient(_, retries - 1))(ec)

          case e@ -\/(_) =>
            if (!client.isClosed()) {
              client.sendOutboundCommand(Command.Disconnect)
            }
            cb(e)
        }

      case Failure(t) => cb (-\/(t))
    }

    manager.getClient(req, fresh = false).onComplete(tryClient(_, 1))(ec)
  }
}
