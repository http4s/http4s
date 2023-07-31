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

import cats.parse.Numbers
import cats.parse.{Parser => P}
import org.http4s.headers.Range.SubRange
import org.http4s.internal.parsing.CommonRules
import org.http4s.util.Renderable
import org.http4s.util.Writer
import org.typelevel.ci._

object `Content-Range` {
  def apply(range: Range.SubRange, length: Option[Long] = None): `Content-Range` =
    `Content-Range`(RangeUnit.Bytes, range, length)

  def apply(start: Long, end: Long): `Content-Range` = apply(Range.SubRange(start, Some(end)), None)

  def apply(start: Long): `Content-Range` =
    apply(Range.SubRange(start, None), None)

  def parse(s: String): ParseResult[`Content-Range`] =
    ParseResult.fromParser(parser, "Invalid Content-Range header")(s)

  val parser: P[`Content-Range`] = {

    val nonNegativeLong = Numbers.digits
      .mapFilter { ds =>
        try Some(ds.toLong)
        catch { case _: NumberFormatException => None }
      }

    // byte-range = first-byte-pos "-" last-byte-pos
    val byteRange = ((nonNegativeLong <* P.char('-')) ~ nonNegativeLong)
      .map { case (first, last) => SubRange(first, last) }

    // byte-range-resp = byte-range "/" ( complete-length / "*" )
    val byteRangeResp =
      (byteRange <* P.char('/')) ~ nonNegativeLong.map(Some(_)).orElse(P.char('*').as(None))

    // byte-content-range = bytes-unit SP ( byte-range-resp / unsatisfied-range )
    // `unsatisfied-range` is not represented
    val byteContentRange =
      ((CommonRules.token.map(RangeUnit(_)) <* P.char(' ')) ~ byteRangeResp)
        .map { case (unit, (range, length)) => `Content-Range`(unit, range, length) }

    // Content-Range = byte-content-range / other-content-range
    // other types of ranges are not supported, `other-content-range` is ignored
    byteContentRange
  }

  val name: CIString = ci"Content-Range"

  implicit val headerInstance: Header[`Content-Range`, Header.Single] =
    Header.createRendered(
      name,
      h =>
        new Renderable {
          def render(writer: Writer): writer.type = {
            writer << h.unit << ' ' << h.range << '/'
            h.length match {
              case Some(l) => writer << l
              case None => writer << '*'
            }
          }
        },
      parse,
    )
}

final case class `Content-Range`(unit: RangeUnit, range: Range.SubRange, length: Option[Long])
