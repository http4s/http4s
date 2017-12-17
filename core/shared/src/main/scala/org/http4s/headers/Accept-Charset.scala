package org.http4s
package headers

import cats.data.NonEmptyList
import org.http4s.parser.HttpHeaderParser
import org.http4s.syntax.nonEmptyList._

object `Accept-Charset` extends HeaderKey.Internal[`Accept-Charset`] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`Accept-Charset`] =
    HttpHeaderParser.ACCEPT_CHARSET(s)
}

final case class `Accept-Charset`(values: NonEmptyList[CharsetRange])
    extends Header.RecurringRenderable {
  def key: `Accept-Charset`.type = `Accept-Charset`
  type Value = CharsetRange

  def qValue(charset: Charset): QValue = {
    def specific = values.collectFirst {
      case cs: CharsetRange.Atom if cs.matches(charset) => cs.qValue
    }
    def splatted = values.collectFirst { case cs: CharsetRange.`*` => cs.qValue }
    specific.orElse(splatted).getOrElse(QValue.Zero)
  }

  @deprecated("Use satisfiedBy(charset)", "0.16.1")
  def isSatisfiedBy(charset: Charset): Boolean = satisfiedBy(charset)

  def satisfiedBy(charset: Charset): Boolean = qValue(charset) > QValue.Zero

  def map(f: CharsetRange => CharsetRange): `Accept-Charset` = `Accept-Charset`(values.map(f))
}
