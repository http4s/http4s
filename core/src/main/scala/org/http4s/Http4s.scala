package org.http4s

trait Http4s
  extends Http4sConstants
  with Http4sInstances
  with Http4sFunctions
  with Http4sSyntax

object Http4s extends Http4s

trait Http4sInstances
  extends scalaz.std.AllInstances
  with util.TaskInstances
  with EntityDecoderInstances
  with HttpVersionInstances
  with WritableInstances
  with CharsetRangeInstances
  with QValueInstances
  with MethodInstances

object Http4sInstances extends Http4sInstances

trait Http4sFunctions
  extends QValueFunctions
  with UriFunctions

object Http4sFunctions extends Http4sFunctions

trait Http4sSyntax
  extends util.CaseInsensitiveStringSyntax
  with MessageSyntax

object Http4sSyntax extends Http4sSyntax

trait Http4sConstants
  extends MethodConstants
  with StatusConstants

object Http4sConstants extends Http4sConstants

