package org.http4s

import com.rossabaker.ci.CIString

object AuthScheme {
  val Basic = CIString("Basic")
  val Digest = CIString("Digest")
  val Bearer = CIString("Bearer")
}
