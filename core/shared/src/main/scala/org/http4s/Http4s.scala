package org.http4s

@deprecated("Use org.http4s.implicits._ instead", "0.20.0-M2")
trait Http4s extends Http4sInstances with Http4sFunctions with syntax.AllSyntax

@deprecated("Use org.http4s.implicits._ instead", "0.20.0-M2")
object Http4s extends Http4s

@deprecated("Import from or use EntityDecoder/EntityEncoder directly instead", "0.20.0-M2")
trait Http4sInstances

@deprecated("Import from or use EntityDecoder/EntityEncoder directly instead", "0.20.0-M2")
object Http4sInstances extends Http4sInstances

@deprecated("Use org.http4s.qvalue._ or org.http4s.Uri._ instead", "0.20.0-M2")
trait Http4sFunctions

@deprecated("Use org.http4s.qvalue._ or org.http4s.Uri._ instead", "0.20.0-M2")
object Http4sFunctions extends Http4sFunctions
