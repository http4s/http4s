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
package parser

import java.nio.charset.StandardCharsets

import org.http4s.headers.{Link, LinkValue}
import org.http4s.internal.parboiled2.support.{::, HNil}
import org.http4s.internal.parboiled2._

private[parser] trait LinkHeader {
  def LINK(value: String): ParseResult[Link] =
    new LinkParser(value).parse

  private class LinkParser(input: ParserInput)
      extends Http4sHeaderParser[Link](input)
      with MediaRange.MediaRangeParser
      with Rfc3986Parser {
    override def charset: java.nio.charset.Charset = StandardCharsets.ISO_8859_1

    def entry: Rule1[Link] =
      rule {
        oneOrMore(LinkValueWithAttr).separatedBy(ListSep) ~> { (links: Seq[LinkValue]) =>
          Link(links.head, links.tail: _*)
        }
      }

    def LinkValueWithAttr: Rule1[LinkValue] =
      rule {
        "<" ~ LinkValue ~ ">" ~ zeroOrMore(";" ~ OptWS ~ LinkValueAttr)
      }

    def LinkValue: Rule1[LinkValue] =
      rule {
        // https://tools.ietf.org/html/rfc3986#section-4.1
        (AbsoluteUri | RelativeRef) ~> { (a: Uri) =>
          headers.LinkValue(a)
        }
      }

    def LinkValueAttr: Rule[LinkValue :: HNil, LinkValue :: HNil] =
      rule {
        "rel=" ~ (Token | QuotedString) ~> { (link: LinkValue, rel: String) =>
          // https://tools.ietf.org/html/rfc8288#section-3.3
          if (link.rel.isDefined)
            link
          else
            link.copy(rel = Some(rel))
        } |
          "rev=" ~ (Token | QuotedString) ~> { (link: LinkValue, rev: String) =>
            link.copy(rev = Some(rev))
          } |
          "title=" ~ (Token | QuotedString) ~> { (link: LinkValue, title: String) =>
            link.copy(title = Some(title))
          } |
          "type=" ~ (MediaRangeDef | ("\"" ~ MediaRangeDef ~ "\"")) ~> {
            (link: LinkValue, `type`: MediaRange) =>
              link.copy(`type` = Some(`type`))
          }
      }
  }
}
