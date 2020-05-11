package org.http4s
package headers

import cats.data.NonEmptyList
import cats.syntax.eq._
import org.http4s.parser.HttpHeaderParser

object `Accept-Encoding` extends HeaderKey.Internal[`Accept-Encoding`] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`Accept-Encoding`] =
    HttpHeaderParser.ACCEPT_ENCODING(s)
}

final case class `Accept-Encoding`(values: NonEmptyList[ContentCoding])
    extends Header.RecurringRenderable {
  def key: `Accept-Encoding`.type = `Accept-Encoding`
  type Value = ContentCoding

  @deprecated("Has confusing semantics in the presence of splat. Do not use.", "0.16.1")
  def preferred: ContentCoding =
    values.tail.fold(values.head)((a, b) => if (a.qValue >= b.qValue) a else b)

  def qValue(coding: ContentCoding): QValue = {
    def specific =
      values.toList.collectFirst {
        case cc: ContentCoding if cc =!= ContentCoding.`*` && cc.matches(coding) => cc.qValue
      }
    def splatted =
      values.toList.collectFirst {
        case cc: ContentCoding if cc === ContentCoding.`*` => cc.qValue
      }
    specific.orElse(splatted).getOrElse(QValue.Zero)
  }

  def satisfiedBy(coding: ContentCoding): Boolean = qValue(coding) > QValue.Zero
}
