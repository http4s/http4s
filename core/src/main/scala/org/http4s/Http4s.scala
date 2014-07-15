package org.http4s

trait Http4s extends HttpBodyFunctions
  with MessageSyntax
  with StatusInstances
  with WritableInstances
  with util.CaseInsensitiveStringSyntax
  with util.TaskInstances

object Http4s extends Http4s
