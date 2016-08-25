package org.http4s
package headers

import org.http4s.parser.HttpHeaderParser
import org.http4s.util.Writer
import org.http4s.{Header, HeaderKey, ParseResult}

object `X-B3-Flags` extends HeaderKey.Internal[`X-B3-Flags`] with HeaderKey.Singleton {
  override def parse(s: String): ParseResult[`X-B3-Flags`] =
    HttpHeaderParser.X_B3_FLAGS(s)

  sealed trait Flag

  object Flag {
    case object Debug extends Flag
    case object Sampled extends Flag
    case object SamplingSet extends Flag
  }

}

final case class `X-B3-Flags`(flags: List[`X-B3-Flags`.Flag]) extends Header.Parsed {
  override def key: `X-B3-Flags`.type = `X-B3-Flags`

  override def renderValue(writer: Writer): writer.type = writer.append(value)
}
