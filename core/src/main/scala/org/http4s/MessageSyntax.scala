package org.http4s

import scalaz.{EitherT, \/}
import scalaz.concurrent.Task

object MessageSyntax extends MessageSyntax

trait MessageSyntax {
  @deprecated("Moved to org.http4s.syntax.TaskRequestSyntax", "0.16")
  implicit def requestSyntax(req: Task[Request]): syntax.TaskRequestOps =
    new syntax.TaskRequestOps(req)

  @deprecated("Moved to org.http4s.syntax.TaskResponseSyntax", "0.16")
  implicit def responseSyntax(resp: Task[Response]): syntax.TaskResponseOps =
    new syntax.TaskResponseOps(resp)
}
