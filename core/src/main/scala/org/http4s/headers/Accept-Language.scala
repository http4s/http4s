package org.http4s
package headers

import cats.data.NonEmptyList
import org.http4s.parser.HttpHeaderParser

object `Accept-Language` extends HeaderKey.Internal[`Accept-Language`] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`Accept-Language`] =
    HttpHeaderParser.ACCEPT_LANGUAGE(s)
}

final case class `Accept-Language`(values: NonEmptyList[LanguageTag])
    extends Header.RecurringRenderable {
  def key: `Accept-Language`.type = `Accept-Language`
  type Value = LanguageTag
  def preferred: LanguageTag = values.tail.fold(values.head)((a, b) => if (a.q >= b.q) a else b)
  def satisfiedBy(languageTag: LanguageTag): Boolean = values.exists(_.satisfiedBy(languageTag))
}
