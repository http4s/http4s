package org.http4s
package parser

import org.http4s.headers.Link
import org.http4s.internal.parboiled2.support.{::, HNil}
import org.http4s.internal.parboiled2.{Rule, Rule1}

trait LinkHeader {
  def LINK(value: String): ParseResult[Link] = new LinkParser(value).parse

  private class LinkParser(value: String)
      extends UriHeaderParser[Link](value)
      with MediaRange.MediaRangeParser {

    override def fromUri(uri: Uri): Link = Link(uri)

    override def entry: Rule1[Link] = rule {
      "<" ~ super[UriHeaderParser].entry ~ ">" ~ zeroOrMore(";" ~ OptWS ~ LinkAttr)
    }

    def LinkAttr: Rule[Link :: HNil, Link :: HNil] = rule {
      "rel=" ~ (Token | QuotedString) ~> { (link: Link, rel: String) =>
        link.copy(rel = Some(rel))
      } |
        "rev=" ~ (Token | QuotedString) ~> { (link: Link, rev: String) =>
          link.copy(rev = Some(rev))
        } |
        "title=" ~ (Token | QuotedString) ~> { (link: Link, title: String) =>
          link.copy(title = Some(title))
        } |
        "type=" ~ (MediaRangeDef | ("\"" ~ MediaRangeDef ~ "\"")) ~> {
          (link: Link, `type`: MediaRange) =>
            link.copy(`type` = Some(`type`))
        }
    }
  }
}
