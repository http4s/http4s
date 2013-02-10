package org.http4s

sealed trait ServerProtocol

object ServerProtocol {
  case object Included extends ServerProtocol
  case class ExtensionServerProtocol(name: String) extends ServerProtocol

  def apply(name: String): ServerProtocol = name match {
    case "HTTP/1.1" => HttpVersion.`Http/1.1`
    case "HTTP/1.0" => HttpVersion.`Http/1.0`
    case name => ExtensionServerProtocol(name)
  }
}

case class HttpVersion(major: Int, minor: Int) extends ServerProtocol

object HttpVersion {
  val `Http/1.1` = HttpVersion(1, 1)
  val `Http/1.0` = HttpVersion(1, 0)
}
