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
package headers

import cats.data.NonEmptyList
import cats.parse.Numbers
import cats.parse.{Parser => P}
import cats.parse.{Parser0 => P0}
import org.http4s.internal.parsing.CommonRules
import org.http4s.util.Renderable
import org.http4s.util.Writer
import org.typelevel.ci._

// See https://datatracker.ietf.org/doc/html/rfc7233

object Range {
  def apply(unit: RangeUnit, r1: SubRange, rs: SubRange*): Range =
    Range(unit, NonEmptyList.of(r1, rs: _*))

  def apply(r1: SubRange, rs: SubRange*): Range = apply(RangeUnit.Bytes, r1, rs: _*)

  def apply(begin: Long, end: Long): Range = apply(SubRange(begin, Some(end)))

  def apply(begin: Long): Range = apply(SubRange(begin, None))

  object SubRange {
    def apply(first: Long): SubRange = SubRange(first, None)
    def apply(first: Long, second: Long): SubRange = SubRange(first, Some(second))
  }

  final case class SubRange(first: Long, second: Option[Long]) extends Renderable {

    /** Base method for rendering this object efficiently */
    override def render(writer: Writer): writer.type = {
      writer << first
      // the trailing '-' is necessary unless this is a suffix range
      if (first >= 0)
        writer << '-'
      second.foreach(writer << _)
      writer
    }
  }

  val parser: P0[Range] = {
    // https://datatracker.ietf.org/doc/html/rfc7233#section-3.1

    def toLong(s: String): Option[Long] =
      try Some(s.toLong)
      catch { case _: NumberFormatException => None }

    val nonNegativeLong = Numbers.digits
      .mapFilter(toLong)

    val negativeLong = (P.char('-') ~ Numbers.digits).string
      .mapFilter(toLong)

    // byte-range-spec = first-byte-pos "-" [ last-byte-pos ]
    val byteRangeSpec = ((nonNegativeLong <* P.char('-')) ~ nonNegativeLong.?)
      .map { case (first, last) => SubRange(first, last) }

    // suffix-byte-range-spec = "-" suffix-length
    val suffixByteRangeSpec = negativeLong.map(SubRange(_))

    // byte-range-set  = 1#( byte-range-spec / suffix-byte-range-spec )
    val byteRangeSet = CommonRules.headerRep1(byteRangeSpec.orElse(suffixByteRangeSpec))

    // byte-ranges-specifier = bytes-unit "=" byte-range-set
    val byteRangesSpecifier =
      ((CommonRules.token.map(RangeUnit(_)) <* P.char('=')) ~ byteRangeSet)
        .map { case (unit, ranges) => Range(unit, ranges) }

    // Range = byte-ranges-specifier / other-ranges-specifier
    // other types of ranges are not supported, `other-ranges-specifier` is ignored
    byteRangesSpecifier
  }

  def parse(s: String): ParseResult[Range] =
    ParseResult.fromParser(parser, "Invalid Range header")(s)

  implicit val headerInstance: Header[Range, Header.Single] =
    Header.createRendered(
      ci"Range",
      h =>
        new Renderable {
          def render(writer: Writer): writer.type = {
            writer << h.unit << '=' << h.ranges.head
            h.ranges.tail.foreach(writer << ',' << _)
            writer
          }
        },
      parse,
    )

}

final case class Range(unit: RangeUnit, ranges: NonEmptyList[Range.SubRange])
