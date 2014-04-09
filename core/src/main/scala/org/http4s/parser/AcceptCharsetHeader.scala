package org.http4s
package parser

import org.parboiled2._
import CharacterSet._
import Header.`Accept-Charset`
import util.CaseInsensitiveString._


private[parser] trait AcceptCharsetHeader {

  def ACCEPT_CHARSET(value: String) = new AcceptCharsetParser(value).parse

  private class AcceptCharsetParser(input: ParserInput) extends Http4sHeaderParser[`Accept-Charset`](input) {
    def entry: Rule1[`Accept-Charset`] = rule {
      oneOrMore(CharsetRangeDecl).separatedBy(ListSep) ~ EOL ~> {xs: Seq[CharacterSet] =>
        Header.`Accept-Charset`(xs.head, xs.tail: _*)
      }
    }

    def CharsetRangeDecl: Rule1[CharacterSet] = rule {
      ("*" ~ CharsetQuality) ~> { q => if (q.intValue != Q.MAX_VALUE) `*`.withQuality(q) else `*` } |
        ((Token ~ CharsetQuality) ~> { (s: String, q: Q) =>
          val c = CharacterSet.getOrElseCreate(s.ci)
          if (q.intValue != Q.MAX_VALUE) c.withQuality(q) else c
        })
    }

    def CharsetQuality: Rule1[Q] = rule {
      (";" ~ OptWS ~ "q" ~ "=" ~ QValue) | push(Q.Unity)
    }
  }



}