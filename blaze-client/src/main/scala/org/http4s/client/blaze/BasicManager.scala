package org.http4s
package client
package blaze

import scalaz.concurrent.Task


/* implementation bits for the basic client manager */
private final class BasicManager (builder: ConnectionBuilder) extends ConnectionManager {
  override def getClient(request: Request, freshClient: Boolean): Task[BlazeClientStage] =
    builder(request)

  override def shutdown(): Task[Unit] = Task(())

  override def recycleClient(request: Request, stage: BlazeClientStage): Unit = stage.shutdown()
}


