package org.http4s
package parser

import org.parboiled2._
import ContentCoding._
import org.http4s.Header.`Accept-Encoding`

private[parser] trait AcceptEncodingHeader {


  def ACCEPT_ENCODING(value: String) = new AcceptEncodingParser(value).parse

  private class AcceptEncodingParser(input: ParserInput) extends Http4sHeaderParser[`Accept-Encoding`](input) {

    def entry: Rule1[`Accept-Encoding`] = rule {
      oneOrMore(EncodingRangeDecl).separatedBy(ListSep) ~ EOL ~> {  xs: Seq[ContentCoding] =>
        Header.`Accept-Encoding`(xs.head, xs.tail: _*)
      }
    }

    def EncodingRangeDecl: Rule1[ContentCoding] = rule {
      (EncodingRangeDef ~ EncodingQuality) ~> { (coding: ContentCoding, q: Q) =>
        if (q eq Q.Unity) coding
        else coding.withQuality(q)
      }
    }

    def EncodingRangeDef: Rule1[ContentCoding] = rule {
      "*" ~ push(`*`) | Token ~> (org.http4s.ContentCoding.resolve(_))
    }

    def EncodingQuality: Rule1[Q] = rule {
      ";" ~ OptWS ~ "q" ~ "=" ~ QValue | push(Q.Unity)
    }
  }
}