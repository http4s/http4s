package org.http4s.parser

import org.http4s.{headers, RangeUnit}
import org.http4s.headers.{`Accept-Ranges`, `Content-Range`, Range}
import org.http4s.headers.Range.SubRange
import org.parboiled2._

import scalaz.NonEmptyList


private[http4s] trait RangeRule extends Parser with AdditionalRules {
    def byteRange: Rule1[SubRange] = rule {
      capture(optional('-') ~ oneOrMore(Digit)) ~ optional('-' ~ capture(oneOrMore(Digit))) ~> { (d1: String, d2: Option[String]) =>
        SubRange(d1.toLong, d2.map(_.toLong))
      }
    }
  }

