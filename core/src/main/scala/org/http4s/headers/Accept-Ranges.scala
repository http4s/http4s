package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.parser.Http4sHeaderParser
import org.http4s.util.Writer
import org.parboiled2._

object `Accept-Ranges` extends HeaderKey.Internal[`Accept-Ranges`] with HeaderKey.Singleton {

  def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply(first +: more)
  def bytes = apply(RangeUnit.Bytes)
  def none = apply(Nil)

  override protected def parseHeader(raw: Raw): Option[`Accept-Ranges`.HeaderT] =
    new AcceptRangesParser(raw.value).parse.toOption

  private class AcceptRangesParser(input: ParserInput) extends Http4sHeaderParser[`Accept-Ranges`](input) {

    def entry: Rule1[`Accept-Ranges`] = rule {
      RangeUnitsDef ~ EOL ~> (headers.`Accept-Ranges`(_: Seq[RangeUnit]))
    }

    def RangeUnitsDef: Rule1[Seq[RangeUnit]] = rule {
      NoRangeUnitsDef | zeroOrMore(RangeUnit).separatedBy(ListSep)
    }

    def NoRangeUnitsDef: Rule1[Seq[RangeUnit]] = rule {
      "none" ~ push(List.empty[RangeUnit])
    }

    /* 3.12 Range Units http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html */

    def RangeUnit: Rule1[RangeUnit] = rule { Token ~> { s: String => org.http4s.RangeUnit(s)} }
  }
}

final case class `Accept-Ranges` private[http4s] (rangeUnits: Seq[RangeUnit]) extends Header.Parsed {
  def key = `Accept-Ranges`
  def renderValue(writer: Writer): writer.type = {
    if (rangeUnits.isEmpty) writer.append("none")
    else {
      writer.append(rangeUnits.head)
      rangeUnits.tail.foreach(r => writer.append(", ").append(r))
      writer
    }
  }
}
