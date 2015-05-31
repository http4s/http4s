package org.http4s
package headers

import java.net.InetAddress

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.Writer

import scalaz.NonEmptyList

object `X-Forwarded-For` extends HeaderKey.Internal[`X-Forwarded-For`] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[`X-Forwarded-For`] = {
    new Http4sHeaderParser[`X-Forwarded-For`](raw.value) {
      def entry = rule {
        oneOrMore((Ip ~> (Some(_)))  | ("unknown" ~ push(None))).separatedBy(ListSep) ~
          EOL ~> { xs: Seq[Option[InetAddress]] =>
          `X-Forwarded-For`(xs.head, xs.tail: _*)
        }
      }
    }.parse.toOption
  }
}

final case class `X-Forwarded-For`(values: NonEmptyList[Option[InetAddress]]) extends Header.Recurring {
  override def key = `X-Forwarded-For`
  type Value = Option[InetAddress]
  override lazy val value = super.value
  override def renderValue(writer: Writer): writer.type = {
    values.head.fold(writer.append("unknown"))(i => writer.append(i.getHostAddress))
    values.tail.foreach(append(writer, _))
    writer
  }

  @inline
  private def append(sb: Writer, add: Option[InetAddress]): Unit = {
    sb.append(", ")
    if (add.isDefined) sb.append(add.get.getHostAddress)
    else sb.append("unknown")
  }
}

