package org.http4s

sealed trait Method

object Method {
  case object Get extends Method
  case object Post extends Method
  case object Head extends Method
  case object Put extends Method
  case object Delete extends Method
  case object Options extends Method
  case object Trace extends Method
  case object Connect extends Method
  case object Patch extends Method
  case class ExtensionMethod(name: String) extends Method
}
