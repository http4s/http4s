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
import cats.parse.Rfc5234
import org.http4s.internal.parsing.CommonRules
import org.http4s.internal.parsing.Rfc2616
import org.typelevel.ci._

object `Content-Language` {
  def apply(head: LanguageTag, tail: LanguageTag*): `Content-Language` =
    apply(NonEmptyList(head, tail.toList))

  def parse(s: String): ParseResult[`Content-Language`] =
    ParseResult.fromParser(parser, "Invalid Content-Language header")(s)

  private[http4s] val parser: Parser[headers.`Content-Language`] = {
    val languageTag: Parser[LanguageTag] =
      (Parser.string(Rfc5234.alpha.rep) ~ (Parser.string("-") *> Rfc2616.token).rep0).map {
        case (main: String, sub: collection.Seq[String]) =>
          LanguageTag(main, QValue.One, sub)
      }
    CommonRules.headerRep1(languageTag).map { tags =>
      headers.`Content-Language`(tags)
    }
  }

  implicit val headerInstance: Header[`Content-Language`, Header.Recurring] =
    Header.createRendered(
      ci"Content-Language",
      _.values,
      parse,
    )

  implicit val headerSemigroupInstance: cats.Semigroup[`Content-Language`] =
    (a, b) => `Content-Language`(a.values.concatNel(b.values))
}

// RFC - https://datatracker.ietf.org/doc/html/rfc3282#page-2
final case class `Content-Language`(values: NonEmptyList[LanguageTag])
