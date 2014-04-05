package org.http4s

import org.http4s.util.CaseInsensitiveString
import CaseInsensitiveString._

sealed case class AuthScheme (name: CaseInsensitiveString)

object AuthScheme extends Registry[CaseInsensitiveString, AuthScheme] {
  def getOrCreate(name: String): AuthScheme = {
    val key = name.ci
    lookup(key).getOrElse(new AuthScheme(key))
  }

  def register(authScheme: AuthScheme): AuthScheme = register(authScheme.name, authScheme)

  val Basic = register(AuthScheme("Basic".ci))
  val Digest = register(AuthScheme("Digest".ci))
  val Bearer = register(AuthScheme("Bearer".ci))
}
