package org.http4s
package syntax

import scalaz.concurrent.Task

trait TaskMaybeResponseSyntax {
  implicit def http4sTaskMaybeResponseSyntax(resp: Task[MaybeResponse]): TaskMaybeResponseOps =
    new TaskMaybeResponseOps(resp)
}

final class TaskMaybeResponseOps(val self: Task[MaybeResponse])
    extends AnyVal {
  def as[T](implicit decoder: EntityDecoder[T]): Task[T] =
    self.flatMap {
      case resp: Response => resp.as[T]
      case pass           => Task.fail(new Exception("Attempted to decode a pass"))
    }
  def status: Task[Status] =
    self.flatMap {
      case resp: Response => Task.now(resp.status)
      case pass           => Task.fail(new Exception("Attempted to retrieve status on a pass"))
    }
}
