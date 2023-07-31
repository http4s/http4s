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
import cats.parse.Parser
import cats.syntax.eq._
import org.typelevel.ci._

object `Accept-Encoding` {
  def apply(head: ContentCoding, tail: ContentCoding*): `Accept-Encoding` =
    apply(NonEmptyList(head, tail.toList))

  def parse(s: String): ParseResult[`Accept-Encoding`] =
    ParseResult.fromParser(parser, "Invalid Accept-Encoding header")(s)

  private[http4s] val parser: Parser[`Accept-Encoding`] = {
    import org.http4s.internal.parsing.CommonRules.headerRep1

    headerRep1(ContentCoding.parser).map(xs => apply(xs))
  }

  implicit val headerInstance: Header[`Accept-Encoding`, Header.Recurring] =
    Header.createRendered(
      ci"Accept-Encoding",
      _.values,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[`Accept-Encoding`] =
    (a, b) => `Accept-Encoding`(a.values.concatNel(b.values))
}

final case class `Accept-Encoding`(values: NonEmptyList[ContentCoding]) {
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
