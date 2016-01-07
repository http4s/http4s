package org.http4s
package client
package blaze

import scalaz.concurrent.Task


/* implementation bits for the basic client manager */
private final class BasicManager (builder: ConnectionBuilder) extends ConnectionManager {
  override def getClient(requestKey: RequestKey): Task[BlazeClientStage] =
    builder(requestKey)

  override def shutdown(): Task[Unit] =
    Task.now(())

  override def releaseClient(request: RequestKey, stage: BlazeClientStage, keepAlive: Boolean): Unit =
    stage.shutdown()
}


