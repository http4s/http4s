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

package org.http4s.headers

import cats.data.NonEmptyList
import cats.parse.{Parser, Parser1}
import org.http4s._
import org.http4s.internal.parsing.Rfc7230.{headerRep1, quotedString, token}
import org.http4s.parser.HttpHeaderParser
import org.http4s.parser.Rfc2616BasicRules.optWs

object Link extends HeaderKey.Internal[Link] with HeaderKey.Recurring {
  override def parse(s: String): ParseResult[Link] =
    HttpHeaderParser.LINK(s)
  // TODO depends on #4095
  //ParseResult.fromParser(parser, "Invalid Link header")(s)

  private[http4s] val parser: Parser1[Link] = {
    import cats.parse.Parser._

    val linkValue: Parser1[LinkValue] =
      // TODO depends on #4095
      Parser.failWith("Not implemented")

    val linkValueAttr: Parser1[LinkValue] = {
      val relParser = (linkValue ~ (string1("rel=") *> token.orElse(quotedString)))
        .map { case (link: LinkValue, rel: String) =>
          // https://tools.ietf.org/html/rfc8288#section-3.3
          if (link.rel.isDefined)
            link
          else
            link.copy(rel = Some(rel))
        }

      val revParser = (linkValue ~ (string1("rev=") *> token.orElse(quotedString))).map {
        case (link: LinkValue, rev: String) =>
          link.copy(rev = Some(rev))
      }

      val titleParser = (linkValue ~ (string1("title=") *> token.orElse(quotedString))).map {
        case (link: LinkValue, title: String) => link.copy(title = Some(title))
      }

      val typeParser = {
        val mediaRange = string1("type=") *> MediaRange.parser.orElse1(
          string1("\"") *> MediaRange.parser <* string1("\""))
        (linkValue ~ mediaRange).map { case (link, tpe) => link.copy(`type` = Some(tpe)) }
      }

      relParser.orElse1(revParser).orElse1(titleParser).orElse1(typeParser)
    }

    val linkValueWithAttr: Parser1[LinkValue] =
      char('<') *> linkValue <* char('>') ~ Parser.rep(char(';') *> optWs *> linkValueAttr)

    headerRep1(linkValueWithAttr).map(links => Link(links.head, links.tail: _*))
  }
}

final case class Link(values: NonEmptyList[LinkValue]) extends Header.RecurringRenderable {
  override def key: Link.type = Link
  type Value = LinkValue
}
