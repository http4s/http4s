package org.http4s

import util.string._


object AuthScheme {
  val Basic = "Basic".ci
  val Digest = "Digest".ci
  val Bearer = "Bearer".ci
}
