package org.http4s
package headers

import cats.data.NonEmptyList
import java.net.InetAddress
import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer

object `X-Forwarded-For` extends HeaderKey.Internal[`X-Forwarded-For`] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`X-Forwarded-For`] =
    HttpHeaderParser.X_FORWARDED_FOR(s)
}

final case class `X-Forwarded-For`(values: NonEmptyList[Option[InetAddress]])
    extends Header.Recurring {
  override def key: `X-Forwarded-For`.type = `X-Forwarded-For`
  type Value = Option[InetAddress]
  override lazy val value = super.value
  override def renderValue(writer: Writer): writer.type = {
    values.head.fold(writer.append("unknown"))(i => writer.append(i.getHostAddress))
    values.tail.foreach(append(writer, _))
    writer
  }

  @inline
  private def append(w: Writer, add: Option[InetAddress]): w.type = {
    w.append(", ")
    if (add.isDefined) w.append(add.get.getHostAddress)
    else w.append("unknown")
  }
}
