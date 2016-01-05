package org.http4s
package client
package blaze

import scalaz.concurrent.Task


/* implementation bits for the basic client manager */
private final class BasicManager (builder: ConnectionBuilder) extends ConnectionManager {
  override def withClient[A](request: Request)(f: BlazeClientStage => Task[A]): Task[A] = {
    builder(RequestKey.fromRequest(request)).flatMap { stage =>
      f(stage).onFinish { _ => Task.delay {
        if (!stage.isClosed)
          stage.shutdown()
      }}
    }
  }

  override def shutdown(): Task[Unit] = Task.now(())
}


