package org.http4s
package parser

import org.parboiled2._
import org.http4s.Header.`Accept-Ranges`

private[parser] trait AcceptRangesHeader {

  def ACCEPT_RANGES(input: String) = new AcceptRangesParser(input).parse

  private class AcceptRangesParser(input: ParserInput) extends Http4sHeaderParser[`Accept-Ranges`](input) {

    def entry: Rule1[`Accept-Ranges`] = rule {
      RangeUnitsDef ~ EOL ~> (Header.`Accept-Ranges`(_: Seq[RangeUnit]))
    }

    def RangeUnitsDef: Rule1[Seq[RangeUnit]] = rule {
      NoRangeUnitsDef | zeroOrMore(RangeUnit).separatedBy(ListSep)
    }

    def NoRangeUnitsDef: Rule1[Seq[RangeUnit]] = rule {
      "none" ~ push(List.empty[RangeUnit])
    }

    /* 3.12 Range Units http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html */

    def RangeUnit: Rule1[RangeUnit] = rule { BytesUnit | OtherRangeUnit }

    def BytesUnit: Rule1[RangeUnit] = rule { "bytes" ~ push(org.http4s.RangeUnit.bytes) }

    def OtherRangeUnit: Rule1[RangeUnit] = rule { Token ~> org.http4s.RangeUnit.CustomRangeUnit }
  }
}