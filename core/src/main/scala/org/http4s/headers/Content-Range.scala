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

import cats.parse.{Numbers, Parser1, Parser => P}
import org.http4s.headers.Range.SubRange
import org.http4s.internal.parsing.Rfc7230
import org.http4s.util.Writer

object `Content-Range` extends HeaderKey.Internal[`Content-Range`] with HeaderKey.Singleton {
  def apply(range: Range.SubRange, length: Option[Long] = None): `Content-Range` =
    `Content-Range`(RangeUnit.Bytes, range, length)

  def apply(start: Long, end: Long): `Content-Range` = apply(Range.SubRange(start, Some(end)), None)

  override def parse(s: String): ParseResult[`Content-Range`] =
    parser.parseAll(s).left.map { e =>
      ParseFailure("Invalid Content-Range header", e.toString)
    }

  val parser: Parser1[`Content-Range`] = {

    val nonNegativeLong = Numbers.digits1
      .mapFilter { ds =>
        try Some(ds.toLong)
        catch { case _: NumberFormatException => None }
      }

    // byte-range = first-byte-pos "-" last-byte-pos
    val byteRange = ((nonNegativeLong <* P.char('-')) ~ nonNegativeLong)
      .map { case (first, last) => SubRange(first, last) }

    // byte-range-resp = byte-range "/" ( complete-length / "*" )
    val byteRangeResp =
      (byteRange <* P.char('/')) ~ nonNegativeLong.map(Some(_)).orElse1(P.char('*').as(None))

    // byte-content-range = bytes-unit SP ( byte-range-resp / unsatisfied-range )
    // `unsatisfied-range` is not represented
    val byteContentRange =
      ((Rfc7230.token.map(RangeUnit(_)) <* P.char(' ')) ~ byteRangeResp)
        .map { case (unit, (range, length)) => `Content-Range`(unit, range, length) }

    // Content-Range = byte-content-range / other-content-range
    // other types of ranges are not supported, `other-content-range` is ignored
    byteContentRange
  }
}

final case class `Content-Range`(unit: RangeUnit, range: Range.SubRange, length: Option[Long])
    extends Header.Parsed {
  override def key: `Content-Range`.type = `Content-Range`

  override def renderValue(writer: Writer): writer.type = {
    writer << unit << ' ' << range << '/'
    length match {
      case Some(l) => writer << l
      case None => writer << '*'
    }
  }
}
