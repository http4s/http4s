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

    def entry: Rule1[Link] = rule {
      oneOrMore(LinkValueWithAttr).separatedBy(ListSep) ~> { (links: Seq[LinkValue]) =>
        Link(links.head, links.tail: _*)
      }
    }

    def LinkValueWithAttr: Rule1[LinkValue] = rule {
      "<" ~ LinkValue ~ ">" ~ zeroOrMore(";" ~ OptWS ~ LinkValueAttr)
    }

    def LinkValue: Rule1[LinkValue] = rule {
      // https://tools.ietf.org/html/rfc3986#section-4.1
      (AbsoluteUri | RelativeRef) ~> { (a: Uri) =>
        headers.LinkValue(a)
      }
    }

    def LinkValueAttr: Rule[LinkValue :: HNil, LinkValue :: HNil] = rule {
      "rel=" ~ (Token | QuotedString) ~> { (link: LinkValue, rel: String) =>
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
