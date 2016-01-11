package org.http4s
package client
package blaze

import scalaz.concurrent.Task


/* implementation bits for the basic client manager */
private final class BasicManager (builder: ConnectionBuilder) extends ConnectionManager {
  override def borrow(requestKey: RequestKey): Task[BlazeClientStage] =
    builder(requestKey)

  override def shutdown(): Task[Unit] =
    Task.now(())

  override def dispose(stage: BlazeClientStage): Task[Unit] =
    Task.delay(stage.shutdown())

  override def release(stage: BlazeClientStage): Task[Unit] =
    dispose(stage)
}


