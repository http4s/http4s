package org.http4s

import org.http4s.util.{CaseInsensitiveString, Registry}
import org.http4s.parser.ServerProtocolParser

/**
 * The Server Protocol of a request.
 *
 * http://www.ietf.org/rfc/rfc3875, section 4.1.16
 */
sealed trait ServerProtocol extends HttpValue[CaseInsensitiveString] {
  def value: CaseInsensitiveString
}

object ServerProtocol extends Registry[CaseInsensitiveString, ServerProtocol] {
  def resolve(s: String): ServerProtocol = getOrElse(s.ci, parse(s))

  private def parse(s: String): ServerProtocol = ServerProtocolParser(s)
    .fold(e => sys.error(e.toString), identity)

  def register[A <: ServerProtocol](serverProtocol: A): serverProtocol.type =
    register(serverProtocol.value, serverProtocol)

  final case class HttpVersion(major: Int, minor: Int) extends ServerProtocol {
    val value: CaseInsensitiveString = s"HTTP/${major}.${minor}".ci
  }
  val `HTTP/1.1`: HttpVersion = register(new HttpVersion(1, 1))
  val `HTTP/1.0`: HttpVersion = register(new HttpVersion(1, 0))

  object INCLUDED extends ServerProtocol {
    val value = "INCLUDED".ci
  }
  register(INCLUDED)

  case class ExtensionVersion(protocol: CaseInsensitiveString, version: Option[Version] = None) extends ServerProtocol
  {
    def value: CaseInsensitiveString = version.fold(protocol) {
      case Version(major, minor) => s"${protocol}/${major}.${minor}".ci
    }
  }

  final case class Version(major: Int, minor: Int)
}

