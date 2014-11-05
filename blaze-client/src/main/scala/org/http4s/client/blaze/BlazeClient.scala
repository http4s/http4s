package org.http4s.client.blaze

import org.http4s.blaze.pipeline.Command
import org.http4s.client.Client
import org.http4s.{Request, Response}
import org.log4s.getLogger

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scalaz.concurrent.Task
import scalaz.stream.Process.eval_
import scalaz.{-\/, \/-}

/** Base on which to implement a BlazeClient */
trait BlazeClient extends PipelineBuilder with Client {
  private[this] val logger = getLogger

  implicit protected def ec: ExecutionContext

  /** Recycle or close the connection
    * Allow for smart reuse or simple closing of a connection after the completion of a request
    * @param request [[Request]] to connect too
    * @param stage the [[BlazeClientStage]] which to deal with
    */
  protected def recycleClient(request: Request, stage: BlazeClientStage): Unit = stage.shutdown()

  /** Get a connection to the provided address
    * @param request [[Request]] to connect too
    * @param fresh if the client should force a new connection
    * @return a Future with the connected [[BlazeClientStage]] of a blaze pipeline
    */
  protected def getClient(request: Request, fresh: Boolean): Future[BlazeClientStage]



  override def prepare(req: Request): Task[Response] = Task.async { cb =>
    def tryClient(client: Try[BlazeClientStage], retries: Int): Unit = client match {
      case Success(client) =>
        client.runRequest(req).runAsync {
          case \/-(r)    =>
            val endgame = eval_(Task.delay {
              if (!client.isClosed()) {
                recycleClient(req, client)
              }
            })

            cb(\/-(r.copy(body = r.body.onComplete(endgame))))

          case -\/(Command.EOF) if retries > 0 =>
            getClient(req, true).onComplete(tryClient(_, retries - 1))

          case e@ -\/(_) =>
            if (!client.isClosed()) {
              client.sendOutboundCommand(Command.Disconnect)
            }
            cb(e)
        }

      case Failure(t) => cb (-\/(t))
    }

    getClient(req, false).onComplete(tryClient(_, 3))
  }
}