package org.http4s
package headers

import org.http4s.ContentCoding._
import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.CaseInsensitiveString
import org.parboiled2._

import scalaz.NonEmptyList

object `Accept-Encoding` extends HeaderKey.Internal[`Accept-Encoding`] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[`Accept-Encoding`] =
    new AcceptEncodingParser(raw.value).parse.toOption

  private class AcceptEncodingParser(input: ParserInput) extends Http4sHeaderParser[`Accept-Encoding`](input) {

    def entry: Rule1[`Accept-Encoding`] = rule {
      oneOrMore(EncodingRangeDecl).separatedBy(ListSep) ~ EOL ~> {  xs: Seq[ContentCoding] =>
        `Accept-Encoding`(xs.head, xs.tail: _*)
      }
    }

    def EncodingRangeDecl: Rule1[ContentCoding] = rule {
      (EncodingRangeDef ~ EncodingQuality) ~> { (coding: ContentCoding, q: QValue) =>
        if (q == org.http4s.QValue.One) coding
        else coding.withQValue(q)
      }
    }

    def EncodingRangeDef: Rule1[ContentCoding] = rule {
      "*" ~ push(`*`) | Token ~> { s: String =>
        val cis = CaseInsensitiveString(s)
        org.http4s.ContentCoding.getOrElseCreate(cis)
      }
    }

    def EncodingQuality: Rule1[QValue] = rule {
      ";" ~ OptWS ~ "q" ~ "=" ~ QValue | push(org.http4s.QValue.One)
    }
  }
}

final case class `Accept-Encoding`(values: NonEmptyList[ContentCoding]) extends Header.RecurringRenderable {
  def key = `Accept-Encoding`
  type Value = ContentCoding
  def preferred: ContentCoding = values.tail.fold(values.head)((a, b) => if (a.qValue >= b.qValue) a else b)
  def satisfiedBy(coding: ContentCoding): Boolean = values.list.exists(_.satisfiedBy(coding))
}
