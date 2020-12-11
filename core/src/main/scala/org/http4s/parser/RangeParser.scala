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
package parser

import cats.data.NonEmptyList
import org.http4s.headers.{Range, `Accept-Ranges`, `Content-Range`}
import org.http4s.headers.Range.SubRange
import org.http4s.internal.parboiled2._

private[parser] trait RangeParser {
  def RANGE(value: String): ParseResult[Range] =
    new Http4sHeaderParser[Range](value) with RangeRule {
      import Range.SubRange

      def entry =
        rule {
          capture(oneOrMore(Alpha)) ~ '=' ~ oneOrMore(byteRange).separatedBy(',') ~> {
            (s: String, rs: Seq[SubRange]) =>
              Range(RangeUnit(s), NonEmptyList.of(rs.head, rs.tail: _*))
          }
        }
    }.parse

  def CONTENT_RANGE(value: String): ParseResult[`Content-Range`] =
    new Http4sHeaderParser[`Content-Range`](value) with RangeRule {
      import Range.SubRange
      def entry =
        rule {
          capture(oneOrMore(Alpha)) ~ ' ' ~ byteRange ~ '/' ~ len ~> {
            (s: String, r: SubRange, len: Option[Long]) =>
              `Content-Range`(RangeUnit(s), r, len)
          }
        }

      def len: Rule1[Option[Long]] =
        rule {
          ('*' ~ push(None)) | (capture(oneOrMore(Digit)) ~> { (s: String) =>
            Some(s.toLong)
          })
        }
    }.parse

  trait RangeRule extends Parser with AdditionalRules {
    def byteRange: Rule1[SubRange] =
      rule {
        capture(optional('-') ~ oneOrMore(Digit)) ~ optional('-' ~ capture(oneOrMore(Digit))) ~> {
          (d1: String, d2: Option[String]) =>
            SubRange(d1.toLong, d2.map(_.toLong))
        }
      }
  }

  def ACCEPT_RANGES(input: String): ParseResult[`Accept-Ranges`] =
    new AcceptRangesParser(input).parse

  private class AcceptRangesParser(input: ParserInput)
      extends Http4sHeaderParser[`Accept-Ranges`](input) {
    def entry: Rule1[`Accept-Ranges`] =
      rule {
        RangeUnitsDef ~ EOL ~> (headers.`Accept-Ranges`(_: List[RangeUnit]))
      }

    def RangeUnitsDef: Rule1[List[RangeUnit]] =
      rule {
        NoRangeUnitsDef | zeroOrMore(RangeUnit).separatedBy(ListSep) ~> {
          (rangeUnits: collection.Seq[RangeUnit]) =>
            rangeUnits.toList
        }
      }

    def NoRangeUnitsDef: Rule1[List[RangeUnit]] =
      rule {
        "none" ~ push(Nil)
      }

    /* 3.12 Range Units http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html */

    def RangeUnit: Rule1[RangeUnit] =
      rule {
        Token ~> { (s: String) =>
          org.http4s.RangeUnit(s)
        }
      }
  }
}
