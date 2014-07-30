package org.http4s

import org.http4s.util.{Writer, Renderable, Registry, CaseInsensitiveString}
import org.http4s.util.string._
import org.http4s.parser.ServerProtocolParser

/**
 * The Server Protocol of a request.
 *
 * http://www.ietf.org/rfc/rfc3875, section 4.1.16
 */
sealed trait ServerProtocol extends Renderable {
  def value: CaseInsensitiveString

  override def render[W <: Writer](writer: W): writer.type = writer ~ value

  override def toString = value.toString
}

object ServerProtocol extends Registry {

  type Key = CaseInsensitiveString
  type Value = ServerProtocol

  implicit def fromValue(v: ServerProtocol): Key = v.value

  implicit def fromKey(k: CaseInsensitiveString): ServerProtocol =
    ServerProtocolParser(k.toString).fold(e => throw new ParseException(e), identity)

  final case class HttpVersion(major: Int, minor: Int) extends ServerProtocol {
    val value: CaseInsensitiveString = s"HTTP/${major}.${minor}".ci
  }

  object HttpVersion {
    def unapply(serverProtocol: ServerProtocol): Option[(Int, Int)] = serverProtocol match {
      case http: HttpVersion => unapply(http)
      case INCLUDED => Some(1 -> 0)
      case _ => None
    }
  }

  val `HTTP/1.1` = registerValue(new HttpVersion(1, 1))
  val `HTTP/1.0` = registerValue(new HttpVersion(1, 0))

  case object INCLUDED extends ServerProtocol {
    val value = "INCLUDED".ci
  }

  registerValue(INCLUDED)

  case class ExtensionVersion(protocol: CaseInsensitiveString, version: Option[Version] = None) extends ServerProtocol
  {
    def value: CaseInsensitiveString = version.fold(protocol) {
      case Version(major, minor) => s"${protocol}/${major}.${minor}".ci
    }
  }

  final case class Version(major: Int, minor: Int)
}

