package org.http4s
package headers

import org.http4s.util.Writer


object `Content-Range` extends HeaderKey.Internal[`Content-Range`] with HeaderKey.Singleton {

  def apply(range: Range.SubRange): `Content-Range` = {
    val length = range.second.fold(None: Option[Long])(l => Some(l - range.first))
    `Content-Range`(RangeUnit.Bytes, range, length)
  }

  def apply(start: Long, end: Long): `Content-Range` = apply(Range.SubRange(start, Some(end)))

  def apply(start: Long): `Content-Range` = apply(Range.SubRange(start, None))
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
