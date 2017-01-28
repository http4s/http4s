package org.http4s
package syntax

import scalaz.concurrent.Task

trait TaskResponseSyntax {
  implicit def http4sTaskResponseSyntax(resp: Task[Response]): TaskResponseOps =
    new TaskResponseOps(resp)
}

final class TaskResponseOps(val self: Task[Response])
    extends AnyVal
    with TaskMessageOps[Response]
    with ResponseOps {
  override def withStatus(status: Status): Self = self.map(_.withStatus(status))
}
