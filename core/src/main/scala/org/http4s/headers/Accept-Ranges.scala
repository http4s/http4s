package org.http4s
package headers

import org.http4s.Header.Raw
import org.http4s.util.Writer

object `Accept-Ranges` extends HeaderKey.Internal[`Accept-Ranges`] with HeaderKey.Singleton {


  override protected def parseHeader(raw: Raw): Option[`Accept-Ranges`.HeaderT] =
    parser.RangeParser.ACCEPT_RANGES(raw.value).toOption

  def apply(first: RangeUnit, more: RangeUnit*): `Accept-Ranges` = apply(first +: more)
  def bytes = apply(RangeUnit.Bytes)
  def none = apply(Nil)
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
