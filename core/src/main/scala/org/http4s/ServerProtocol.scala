package org.http4s

import org.http4s.util.{CaseInsensitiveString, Registry}

sealed abstract class ServerProtocol(protocol: CaseInsensitiveString, version: Option[ServerProtocol.Version] = None)
  extends HttpValue[CaseInsensitiveString]

/**
 * The Server Protocol of a request.
 *
 * http://www.ietf.org/rfc/rfc3875, section 4.1.16
 */
object ServerProtocol extends Registry[CaseInsensitiveString, ServerProtocol] {
  sealed case class Version(major: Int, minor: Int)

  // TODO replace with proper parser
  def apply(name: String): ServerProtocol = getForKey(name.ci).getOrElse(name match {
    case ProtocolRegex(protocol, major, minor) if protocol.equalsIgnoreCase("HTTP") =>
      HttpVersion(major.toInt, minor.toInt)
    case ProtocolRegex(protocol, major, minor) =>
      ExtensionVersion(protocol.ci, Some(Version(major.toInt, minor.toInt)))
    case _ =>
      ExtensionVersion(name.ci, None)
  })

  def register[A <: ServerProtocol](serverProtocol: A): serverProtocol.type =
    register(serverProtocol.value, serverProtocol)

  sealed case class HttpVersion(major: Int, minor: Int) extends ServerProtocol("HTTP".ci, Some(Version(major, minor))) {
    val value: CaseInsensitiveString = s"HTTP/${major}.${minor}".ci
  }
  val `HTTP/1.1`: HttpVersion = register(new HttpVersion(1, 1))
  val `HTTP/1.0`: HttpVersion = register(new HttpVersion(1, 0))

  case object INCLUDED extends ServerProtocol("INCLUDED".ci) {
    val value = "INCLUDED".ci
  }
  register(INCLUDED)

  sealed case class ExtensionVersion(protocol: CaseInsensitiveString, version: Option[Version] = None)
    extends ServerProtocol(protocol, version)
  {
    def value: CaseInsensitiveString = version.fold(protocol) {
      case Version(major, minor) => s"${protocol}/${major}.${minor}".ci
    }
  }

  // TODO create a proper parser
  private val ProtocolRegex = """(.*?)(?:/(\d+).(\d+))""".r
}
