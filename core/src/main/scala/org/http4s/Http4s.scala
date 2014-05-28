package org.http4s

trait Http4s extends HttpBodyFunctions
  with MessageSyntax
  with StatusInstances
  with WritableInstances
  with middleware.PushSupport.PushSyntax
  with util.CaseInsensitiveStringSyntax
  with util.JodaTimeInstances
  with util.JodaTimeSyntax
  with util.TaskInstances

object Http4s extends Http4s
