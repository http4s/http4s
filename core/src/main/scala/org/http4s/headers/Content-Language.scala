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
import cats.parse.{Parser, Rfc5234}
import org.http4s.internal.parsing.{Rfc2616, Rfc7230}
import org.http4s.parser.Rfc2616BasicRules

object `Content-Language` extends HeaderKey.Internal[`Content-Language`] with HeaderKey.Recurring {
  override def parse(s: String): org.http4s.ParseResult[`Content-Language`] =
    ParseResult.fromParser(parser, "Invalid Content-Language header")(s)

  private[http4s] val parser: Parser[headers.`Content-Language`] = {
    val languageTag: Parser[LanguageTag] =
      (Parser.string(Rfc5234.alpha.rep) ~ (Parser.string("-") *> Rfc2616.token).rep0).map {
        case (main: String, sub: collection.Seq[String]) =>
          LanguageTag(main, org.http4s.QValue.One, sub.toList)
      }
    Rfc7230.headerRep1(languageTag).map { tags =>
      headers.`Content-Language`(tags)
    }
  }

}

//RFC - https://tools.ietf.org/html/rfc3282#page-2
final case class `Content-Language`(values: NonEmptyList[LanguageTag])
    extends Header.RecurringRenderable {
  override def key: `Content-Language`.type = `Content-Language`
  type Value = LanguageTag
}
