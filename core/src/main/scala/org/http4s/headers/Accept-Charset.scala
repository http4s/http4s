/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package headers

import cats.data.NonEmptyList
import cats.syntax.foldable._
import org.http4s.parser.HttpHeaderParser
import CharsetRange.Atom

object `Accept-Charset` extends HeaderKey.Internal[`Accept-Charset`] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`Accept-Charset`] =
    HttpHeaderParser.ACCEPT_CHARSET(s)
}

/** {{{
  *   The "Accept-Charset" header field can be sent by a user agent to
  *   indicate what charsets are acceptable in textual response content.
  *   This field allows user agents capable of understanding more
  * }}}
  *
  * From [http//tools.ietf.org/html/rfc7231#section-5.3.3 RFC-7231].
  */
final case class `Accept-Charset`(values: NonEmptyList[CharsetRange])
    extends Header.RecurringRenderable {
  def key: `Accept-Charset`.type = `Accept-Charset`
  type Value = CharsetRange

  def qValue(charset: Charset): QValue = {
    def specific =
      values.collectFirst { case cs: Atom if cs.matches(charset) => cs.qValue }
    def splatted =
      values.collectFirst { case cs: CharsetRange.`*` => cs.qValue }

    specific.orElse(splatted).getOrElse(QValue.Zero)
  }

  @deprecated("Use satisfiedBy(charset)", "0.16.1")
  def isSatisfiedBy(charset: Charset): Boolean = satisfiedBy(charset)

  def satisfiedBy(charset: Charset): Boolean = qValue(charset) > QValue.Zero

  def map(f: CharsetRange => CharsetRange): `Accept-Charset` = `Accept-Charset`(values.map(f))
}
