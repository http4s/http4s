package org.http4s

import org.typelevel.ci.CIString

object AuthScheme {
  val Basic = CIString("Basic")
  val Digest = CIString("Digest")
  val Bearer = CIString("Bearer")
}
