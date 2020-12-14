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
import cats.syntax.either._
import org.http4s.internal.parsing.Rfc7230
import org.http4s.parser.Rfc2616BasicRules

object `Accept-Language` extends HeaderKey.Internal[`Accept-Language`] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[`Accept-Language`] =
    parser.parseAll(s).leftMap { e =>
      ParseFailure("Invalid Accept Language header", e.toString)
    }

  private[http4s] val parser: Parser1[`Accept-Language`] = {
    import Rfc2616BasicRules._
    import cats.parse.Parser.{char => ch, _}
    import cats.parse.Rfc5234._

    val languageTag =
      (string1(alpha.rep1(1)) ~ (ch('-') *> Rfc7230.token).rep ~ QValue.parser).map {
        case ((main, sub), q) => LanguageTag(main, q, sub)
      }

    rep1Sep(languageTag, 1, listSep).map(tags => `Accept-Language`(tags.head, tags.tail: _*))
  }
}

/** Request header used to indicate which natural language would be preferred for the response
  * to be translated into.
  *
  * [[https://tools.ietf.org/html/rfc7231#section-5.3.5 RFC-7231 Section 5.3.5]]
  */
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
