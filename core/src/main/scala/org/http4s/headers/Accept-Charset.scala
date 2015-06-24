package org.http4s
package headers

import scalaz.NonEmptyList

object `Accept-Charset` extends HeaderKey.Internal[`Accept-Charset`] with HeaderKey.Recurring

final case class `Accept-Charset`(values: NonEmptyList[CharsetRange]) extends Header.RecurringRenderable {
  def key = `Accept-Charset`
  type Value = CharsetRange

  def qValue(charset: Charset): QValue = {
    def specific = values.list.collectFirst { case cs: CharsetRange.Atom => cs.qValue }
    def splatted = values.list.collectFirst { case cs: CharsetRange.`*` => cs.qValue }
    specific orElse splatted getOrElse QValue.Zero
  }

  def isSatisfiedBy(charset: Charset) = qValue(charset) > QValue.Zero

  def map(f: CharsetRange => CharsetRange): `Accept-Charset` = `Accept-Charset`(values.map(f))
}