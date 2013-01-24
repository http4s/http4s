package org.http4s

sealed trait ServerProtocol

object ServerProtocol {
  case object Included extends ServerProtocol

  case class ExtensionServerProtocol(name: String) extends ServerProtocol
}

case class HttpVersion(major: Int, minor: Int) extends ServerProtocol

object HttpVersion {
  val Http_1_1 = HttpVersion(1, 1)
  val Http_1_0 = HttpVersion(1, 0)
}