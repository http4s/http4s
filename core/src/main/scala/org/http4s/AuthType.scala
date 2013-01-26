package org.http4s

sealed trait AuthType

object AuthType {
  case object Digest extends AuthType
  case object Basic extends AuthType
  case class ExtensionAuth(name: String) extends AuthType
}
