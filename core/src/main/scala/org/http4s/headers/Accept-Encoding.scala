/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package headers

import cats.data.NonEmptyList
import cats.parse.Parser1
import cats.syntax.eq._

object `Accept-Encoding` extends HeaderKey.Internal[`Accept-Encoding`] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`Accept-Encoding`] =
    ParseResult.fromParser(parser, "Invalid Accept-Encoding header")(s)

  private[http4s] val parser: Parser1[`Accept-Encoding`] = {
    import org.http4s.internal.parsing.Rfc7230.headerRep1

    headerRep1(ContentCoding.parser).map(xs => `Accept-Encoding`(xs.head, xs.tail: _*))
  }
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
