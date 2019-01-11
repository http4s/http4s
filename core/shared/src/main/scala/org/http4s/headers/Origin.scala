package org.http4s
package headers

import cats.data.NonEmptyList
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.{Renderable, Writer}

sealed abstract class Origin extends Header.Parsed {
  def key: Origin.type =
    Origin
}

object Origin extends HeaderKey.Internal[Origin] with HeaderKey.Singleton {
  // An Origin header may be the string "null", representing an "opaque origin":
  // https://stackoverflow.com/questions/42239643/when-does-firefox-set-the-origin-header-to-null-in-post-requests
  case object Null extends Origin {
    def renderValue(writer: Writer): writer.type =
      writer << "null"
  }

  // If the Origin is not "null", it is a non-empty list of Hosts:
  // http://tools.ietf.org/html/rfc6454#section-7
  final case class HostList(hosts: NonEmptyList[Host]) extends Origin {
    def renderValue(writer: Writer): writer.type = {
      writer << hosts.head
      hosts.tail.foreach { host =>
        writer << " "
        writer << host
      }
      writer
    }
  }

  // A host in an Origin header isn't a full URI.
  // It only contains a scheme, a host, and an optional port.
  // Hence we re-used parts of the Uri class here, but we don't use a whole Uri:
  // http://tools.ietf.org/html/rfc6454#section-7
  final case class Host(scheme: Uri.Scheme, host: Uri.Host, port: Option[Int] = None)
      extends Renderable {
    def toUri: Uri =
      Uri(scheme = Some(scheme), authority = Some(Uri.Authority(host = host, port = port)))

    def render(writer: Writer): writer.type =
      toUri.render(writer)
  }

  override def parse(s: String): ParseResult[Origin] =
    HttpHeaderParser.ORIGIN(s)
}
