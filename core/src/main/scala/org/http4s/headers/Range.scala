package org.http4s
package headers

import org.http4s.util.{Renderable, Writer}

import scalaz.NonEmptyList

// See https://tools.ietf.org/html/rfc7233

object Range extends HeaderKey.Internal[Range] with HeaderKey.Singleton {

  def apply(unit: RangeUnit, r1: SubRange, rs: SubRange*): Range =
    Range(unit, NonEmptyList(r1, rs:_*))

  def apply(r1: SubRange, rs: SubRange*): Range = apply(Bytes, r1, rs:_*)

  def apply(begin: Long, end: Long): Range = apply(SubRange(begin, Some(end)))

  def apply(begin: Long): Range = apply(SubRange(begin, None))

  case class RangeUnit(unit: String)

  val Bytes = RangeUnit("bytes")    // The only range-unit defined in rfc7233

  case class SubRange(first: Long, second: Option[Long]) extends Renderable {
    /** Base method for rendering this object efficiently */
    override def render(writer: Writer): writer.type = {
      writer << first
      second.foreach( writer << '-' << _ )
      writer
    }
  }


}

case class Range(unit: Range.RangeUnit, ranges: NonEmptyList[Range.SubRange]) extends Header.Parsed {
  override def key = Range
  override def renderValue(writer: Writer): writer.type = {
    writer << unit.unit << '=' << ranges.head
    ranges.tail.foreach( writer << ',' << _)
    writer
  }
}

