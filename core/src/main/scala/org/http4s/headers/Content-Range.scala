package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.util.Writer


object `Content-Range` extends HeaderKey.Internal[`Content-Range`] with HeaderKey.Singleton {

  override protected def parseHeader(raw: Raw): Option[`Content-Range`.HeaderT] =
    parser.RangeParser.CONTENT_RANGE(raw.value).toOption

  def apply(range: Range.SubRange, length: Option[Long] = None): `Content-Range` = {
    `Content-Range`(RangeUnit.Bytes, range, length)
  }

  def apply(start: Long, end: Long): `Content-Range` = apply(Range.SubRange(start, Some(end)), None)

  def apply(start: Long): `Content-Range` = apply(Range.SubRange(start, None), None)
}

case class `Content-Range`(unit: RangeUnit, range: Range.SubRange, length: Option[Long]) extends Header.Parsed {
  override def key = `Content-Range`

  override def renderValue(writer: Writer): writer.type = {
    writer << unit << ' ' << range << '/'
    length match {
      case Some(l) => writer << l
      case None    => writer << '*'
    }
  }
}
