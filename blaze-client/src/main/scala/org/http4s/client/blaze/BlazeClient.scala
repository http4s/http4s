package org.http4s.client.blaze

import org.http4s.blaze.pipeline.Command
import org.http4s.client.Client
import org.http4s.{Request, Response}

import scala.concurrent.duration.Duration
import scalaz.concurrent.Task
import scalaz.stream.Process.eval_
import scalaz.{-\/, \/-}

/** Blaze client implementation */
final class BlazeClient(manager: ConnectionManager, idleTimeout: Duration, requestTimeout: Duration) extends Client {

  /** Shutdown this client, closing any open connections and freeing resources */
  override def shutdown(): Task[Unit] = manager.shutdown()

  override def prepare(req: Request): Task[Response] = Task.suspend {
    def tryClient(flushPrelude: Boolean)(client: BlazeClientStage): Task[Response] = {
      // Add the timeout stage to the pipeline
      val ts = new ClientTimeoutStage(idleTimeout, requestTimeout, bits.ClientTickWheel)
      client.spliceBefore(ts)
      ts.initialize()

      client.runRequest(req, flushPrelude).attempt.flatMap {
        case \/-(r)    =>
          val recycleProcess = eval_(Task.delay {
            if (!client.isClosed()) {
              ts.removeStage
            }
          })
          Task.now(r.copy(body = r.body.onComplete(recycleProcess)))

        case -\/(Command.EOF) =>
          manager.withClient(req)(tryClient(flushPrelude))

        case -\/(e) =>
          if (!client.isClosed()) client.shutdown()
          Task.fail(e)
      }
    }

    val flushPrelude = !req.body.isHalt
    manager.withClient(req)(tryClient(flushPrelude))
  }
}

