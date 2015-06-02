package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.{RangeRule, Http4sHeaderParser}
import org.http4s.util.Writer
import org.parboiled2._


object `Content-Range` extends HeaderKey.Internal[`Content-Range`] with HeaderKey.Singleton {

  def apply(range: Range.SubRange, length: Option[Long] = None): `Content-Range` = {
    `Content-Range`(RangeUnit.Bytes, range, length)
  }

  def apply(start: Long, end: Long): `Content-Range` = apply(Range.SubRange(start, Some(end)), None)

  def apply(start: Long): `Content-Range` = apply(Range.SubRange(start, None), None)

  override protected def parseHeader(raw: Raw): Option[`Content-Range`.HeaderT] =
    new Http4sHeaderParser[`Content-Range`](raw.value) with RangeRule {
      import Range.SubRange
      def entry = rule {
        capture(oneOrMore(Alpha)) ~ ' ' ~ byteRange ~ '/' ~ len  ~> { (s: String, r: SubRange, len: Option[Long]) =>
          `Content-Range`(RangeUnit(s), r, len)
        }
      }

      def len: Rule1[Option[Long]] = rule {
        ('*' ~ push(None)) | (capture(oneOrMore(Digit)) ~> { s: String => Some(s.toLong)})
      }

    }.parse.toOption
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
