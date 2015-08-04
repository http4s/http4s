package org.http4s.client.blaze

import org.http4s.{Uri, Request}

import scalaz.concurrent.Task

trait ConnectionManager {

  /** Shutdown this client, closing any open connections and freeing resources */
  def shutdown(): Task[Unit]

  /** Get a connection to the provided address
    * @param uri [[Uri]] to connect too
    * @param fresh if the client should force a new connection
    * @return a Future with the connected [[BlazeClientStage]] of a blaze pipeline
    */
  def getClient(uri: Uri, fresh: Boolean): Task[BlazeClientStage]
  
  /** Recycle or close the connection
    * Allow for smart reuse or simple closing of a connection after the completion of a request
    * @param request [[Request]] to connect too
    * @param stage the [[BlazeClientStage]] which to deal with
    */
  def recycleClient(request: Request, stage: BlazeClientStage): Unit = stage.shutdown()
}
