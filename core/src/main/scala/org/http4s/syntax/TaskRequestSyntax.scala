package org.http4s
package syntax

import fs2.Task

trait TaskRequestSyntax {
  implicit def http4sTaskRequestSyntax(req: Task[Request]): TaskRequestOps =
    new TaskRequestOps(req)
}

final class TaskRequestOps(val self: Task[Request])
    extends AnyVal
    with TaskMessageOps[Request]
    with RequestOps {
  def decodeWith[A](decoder: EntityDecoder[A], strict: Boolean)(f: A => Task[Response]): Task[Response] =
    self.flatMap(_.decodeWith(decoder, strict)(f))

  def withPathInfo(pi: String): Task[Request] =
    self.map(_.withPathInfo(pi))
}
