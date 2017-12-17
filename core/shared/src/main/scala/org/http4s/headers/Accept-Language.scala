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

  @deprecated("Has confusing semantics in the presence of splat. Do not use.", "0.16.1")
  def preferred: LanguageTag = values.tail.fold(values.head)((a, b) => if (a.q >= b.q) a else b)

  def qValue(languageTag: LanguageTag): QValue =
    values.toList
      .collect {
        case tag: LanguageTag if tag.matches(languageTag) =>
          if (tag.primaryTag == "*") (0, tag.q)
          else (1 + tag.subTags.size, tag.q)
      }
      .sortBy(-_._1)
      .headOption
      .fold(QValue.Zero)(_._2)

  def satisfiedBy(languageTag: LanguageTag): Boolean = qValue(languageTag) > QValue.Zero
}
