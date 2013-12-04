package org.http4s

import org.http4s.util.CaseInsensitiveString

sealed trait AuthType

object AuthType {
  case object Digest extends AuthType
  case object Basic extends AuthType
  case object Bearer extends AuthType
  case class ExtensionAuth(name: CaseInsensitiveString) extends AuthType
}
