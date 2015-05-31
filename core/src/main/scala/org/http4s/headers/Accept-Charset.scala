package org.http4s
package headers

import org.http4s.CharsetRange.`*`
import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.parboiled2._

import scalaz.NonEmptyList

object `Accept-Charset` extends HeaderKey.Internal[`Accept-Charset`] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[`Accept-Charset`] =
    new AcceptCharsetParser(raw.value).parse.toOption

  private class AcceptCharsetParser(input: ParserInput) extends Http4sHeaderParser[headers.`Accept-Charset`](input) {
    def entry: Rule1[headers.`Accept-Charset`] = rule {
      oneOrMore(CharsetRangeDecl).separatedBy(ListSep) ~ EOL ~> {xs: Seq[CharsetRange] =>
        headers.`Accept-Charset`(xs.head, xs.tail: _*)
      }
    }

    def CharsetRangeDecl: Rule1[CharsetRange] = rule {
      ("*" ~ CharsetQuality) ~> { q => if (q != org.http4s.QValue.One) `*`.withQValue(q) else `*` } |
        ((Token ~ CharsetQuality) ~> { (s: String, q: QValue) =>
          // TODO handle tokens that aren't charsets
          val c = Charset.fromString(s).valueOr(e => throw new ParseException(e))
          if (q != org.http4s.QValue.One) c.withQuality(q) else c.toRange
        })
    }

    def CharsetQuality: Rule1[QValue] = rule {
      (";" ~ OptWS ~ "q" ~ "=" ~ QValue) | push(org.http4s.QValue.One)
    }
  }
}

final case class `Accept-Charset`(values: NonEmptyList[CharsetRange]) extends Header.RecurringRenderable {
  def key = `Accept-Charset`
  type Value = CharsetRange

  def qValue(charset: Charset): QValue = {
    def specific = values.list.collectFirst { case cs: CharsetRange.Atom => cs.qValue }
    def splatted = values.list.collectFirst { case cs: CharsetRange.`*` => cs.qValue }
    def default = if (charset == Charset.`ISO-8859-1`) QValue.One else QValue.Zero
    specific orElse splatted getOrElse default
  }

  def isSatisfiedBy(charset: Charset) = qValue(charset) > QValue.Zero

  def map(f: CharsetRange => CharsetRange): `Accept-Charset` = `Accept-Charset`(values.map(f))
}