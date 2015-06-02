package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.{MediaParser, Http4sHeaderParser}
import org.parboiled2._

import scalaz.NonEmptyList

object `Accept-Language` extends HeaderKey.Internal[`Accept-Language`] with HeaderKey.Recurring {
  override protected def parseHeader(raw: Raw): Option[`Accept-Language`] =
    new AcceptLanguageParser(raw.value).parse.toOption

  private class AcceptLanguageParser(value: String)
    extends Http4sHeaderParser[headers.`Accept-Language`](value) with MediaParser {
    def entry: Rule1[headers.`Accept-Language`] = rule {
      oneOrMore(languageTag).separatedBy(ListSep) ~> { tags: Seq[LanguageTag] =>
        headers.`Accept-Language`(tags.head, tags.tail:_*)
      }
    }

    def languageTag: Rule1[LanguageTag] = rule {
      capture(oneOrMore(Alpha)) ~ zeroOrMore("-" ~ Token) ~ TagQuality ~>
        { (main: String, sub: Seq[String], q: QValue) => LanguageTag(main, q, sub) }
    }


    def TagQuality: Rule1[QValue] = rule {
      (";" ~ OptWS ~ "q" ~ "=" ~ QValue) | push(org.http4s.QValue.One)
    }
  }
}

final case class `Accept-Language`(values: NonEmptyList[LanguageTag]) extends Header.RecurringRenderable {
  def key = `Accept-Language`
  type Value = LanguageTag
  def preferred: LanguageTag = values.tail.fold(values.head)((a, b) => if (a.q >= b.q) a else b)
  def satisfiedBy(languageTag: LanguageTag) = values.list.exists(_.satisfiedBy(languageTag))
}
