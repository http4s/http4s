package org.http4s

import org.http4s.util.{Registry, CaseInsensitiveString}
import util.string._

sealed case class AuthScheme (name: CaseInsensitiveString)

object AuthScheme extends Registry{
  type Key = CaseInsensitiveString
  type Value = AuthScheme

  implicit def fromValue(v: AuthScheme) = v.name
  implicit def fromKey(k: Key) = AuthScheme(k)

  val Basic = registerKey("Basic".ci)
  val Digest = registerKey("Digest".ci)
  val Bearer = registerKey("Bearer".ci)
}
