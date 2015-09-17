package org.http4s
package client
package blaze

import scalaz.concurrent.Task


object BasicManager {

  /** Create a [[ConnectionManager]] that creates new connections on each request
  *
  * @param builder generator of new connections
  * */
  def apply(builder: ConnectionBuilder): ConnectionManager = new BasicManager(builder)
}

private final class BasicManager private(builder: ConnectionBuilder) extends ConnectionManager {
  override def getClient(request: Request, freshClient: Boolean): Task[BlazeClientStage] =
    builder(request)

  override def shutdown(): Task[Unit] = Task(())

  override def recycleClient(request: Request, stage: BlazeClientStage): Unit = stage.shutdown()
}


