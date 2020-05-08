/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Based on https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/AdditionalRules.scala
 * Copyright (C) 2011-2012 spray.io
 * Based on code copyright (C) 2010-2011 by the BlueEyes Web Framework Team
 *
 * Based on https://github.com/akka/akka-http/blob/5932237a86a432d623fafb1e84eeeff56d7485fe/akka-http-core/src/main/scala/akka/http/impl/model/parser/IpAddressParsing.scala
 * Copyright (C) 2009-2017 Lightbend Inc. <http://www.lightbend.com>
 */

package org.http4s
package parser

import cats.implicits._
import java.time.{ZoneOffset, ZonedDateTime}
import org.http4s.internal.parboiled2._
import org.http4s.internal.parboiled2.support.{::, HNil}
import scala.util.Try

private[http4s] trait AdditionalRules extends Rfc2616BasicRules { this: Parser =>
  // scalastyle:off public.methods.have.type

  def EOL: Rule0 = rule { OptWS ~ EOI } // Strip trailing whitespace

  def Digits: Rule1[String] = rule { capture(oneOrMore(Digit)) }

  def Value = rule { Token | QuotedString }

  def Parameter: Rule1[(String, String)] = rule {
    Token ~ "=" ~ OptWS ~ Value ~> ((_: String, _: String))
  }

  def HttpDate: Rule1[HttpDate] = rule { (RFC1123Date | RFC850Date | ASCTimeDate) }

  // RFC1123 date string, e.g. `Sun, 06 Nov 1994 08:49:37 GMT`
  def RFC1123Date: Rule1[HttpDate] = rule {
    // TODO: hopefully parboiled2 will get more helpers so we don't need to chain methods to get under 5 args
    Wkday ~ str(", ") ~ Date1 ~ ch(' ') ~ Time ~ ch(' ') ~ ("GMT" | "UTC") ~> {
      (wkday: Int, day: Int, month: Int, year: Int, hour: Int, min: Int, sec: Int) =>
        createDateTime(year, month, day, hour, min, sec, wkday)
    }
  }

  // RFC 850 date string, e.g. `Sunday, 06-Nov-94 08:49:37 GMT`
  def RFC850Date: Rule1[HttpDate] = rule {
    // TODO: hopefully parboiled2 will get more helpers so we don't need to chain methods to get under 5 args
    Weekday ~ str(", ") ~ Date2 ~ ch(' ') ~ Time ~ ch(' ') ~ ("GMT" | "UTC") ~> {
      (wkday: Int, day: Int, month: Int, year: Int, hour: Int, min: Int, sec: Int) =>
        // We'll assume that if the date is less than 100 it is missing the 1900 part
        val fullYear = if (year < 100) 1900 + year else year
        createDateTime(fullYear, month, day, hour, min, sec, wkday)
    }
  }

  // ANSI C's asctime() format, e.g. `Sun Nov  6 08:49:37 1994`
  def ASCTimeDate: Rule1[HttpDate] = rule {
    Wkday ~ ch(' ') ~ Date3 ~ ch(' ') ~ Time ~ ch(' ') ~ Digit4 ~> {
      (wkday: Int, month: Int, day: Int, hour: Int, min: Int, sec: Int, year: Int) =>
        createDateTime(year, month, day, hour, min, sec, wkday)
    }
  }

  def Date1: RuleN[Int :: Int :: Int :: HNil] = rule {
    (Digit2 | Digit1) ~ ch(' ') ~ Month ~ ch(' ') ~ Digit4
  }

  def Date2: RuleN[Int :: Int :: Int :: HNil] = rule {
    (Digit2 | Digit1) ~ ch('-') ~ Month ~ ch('-') ~ (Digit2 | Digit4)
  }

  def Date3: Rule2[Int, Int] = rule { Month ~ ch(' ') ~ (Digit2 | ch(' ') ~ Digit1) }

  def Time: RuleN[Int :: Int :: Int :: HNil] = rule { Digit2 ~ ch(':') ~ Digit2 ~ ch(':') ~ Digit2 }

  // scalastyle:off magic.number
  def Wkday: Rule1[Int] = rule {
    ("Sun" ~ push(0)) |
      ("Mon" ~ push(1)) |
      ("Tue" ~ push(2)) |
      ("Wed" ~ push(3)) |
      ("Thu" ~ push(4)) |
      ("Fri" ~ push(5)) |
      ("Sat" ~ push(6))
  }

  def Weekday: Rule1[Int] = rule {
    ("Sunday" ~ push(0)) |
      ("Monday" ~ push(1)) |
      ("Tuesday" ~ push(2)) |
      ("Wednesday" ~ push(3)) |
      ("Thursday" ~ push(4)) |
      ("Friday" ~ push(5)) |
      ("Saturday" ~ push(6))
  }

  def Month: Rule1[Int] = rule {
    ("Jan" ~ push(1)) |
      ("Feb" ~ push(2)) |
      ("Mar" ~ push(3)) |
      ("Apr" ~ push(4)) |
      ("May" ~ push(5)) |
      ("Jun" ~ push(6)) |
      ("Jul" ~ push(7)) |
      ("Aug" ~ push(8)) |
      ("Sep" ~ push(9)) |
      ("Oct" ~ push(10)) |
      ("Nov" ~ push(11)) |
      ("Dec" ~ push(12))
  }
  // scalastyle:on magic.number

  def Digit1: Rule1[Int] = rule {
    capture(Digit) ~> { (s: String) =>
      s.toInt
    }
  }

  def Digit2: Rule1[Int] = rule {
    capture(Digit ~ Digit) ~> { (s: String) =>
      s.toInt
    }
  }

  def Digit3: Rule1[Int] = rule {
    capture(Digit ~ Digit ~ Digit) ~> { (s: String) =>
      s.toInt
    }
  }

  def Digit4: Rule1[Int] = rule {
    capture(Digit ~ Digit ~ Digit ~ Digit) ~> { (s: String) =>
      s.toInt
    }
  }

  def NegDigit1: Rule1[Int] = rule {
    "-" ~ capture(Digit) ~> { (s: String) =>
      s.toInt
    }
  }

  private def createDateTime(
      year: Int,
      month: Int,
      day: Int,
      hour: Int,
      min: Int,
      sec: Int,
      wkday: Int): HttpDate =
    Try {
      org.http4s.HttpDate.unsafeFromZonedDateTime(
        ZonedDateTime.of(year, month, day, hour, min, sec, 0, ZoneOffset.UTC))
    }.getOrElse {
      // TODO Would be better if this message had the real input.
      throw new ParseFailure("Invalid date", s"$wkday $year-$month-$day $hour:$min:$sec")
    }

  /* 3.9 Quality Values */

  def QValue: Rule1[QValue] = rule {
    // more loose than the spec which only allows 1 to max. 3 digits/zeros
    (capture(ch('0') ~ optional(ch('.') ~ oneOrMore(Digit))) ~>
      (org.http4s.QValue.fromString(_).valueOr(e => throw e.copy(sanitized = "Invalid q-value")))) |
      (ch('1') ~ optional(ch('.') ~ zeroOrMore(ch('0'))) ~ push(org.http4s.QValue.One))
  }

  /* 2.3 ETag http://tools.ietf.org/html/rfc7232#section-2.3
     entity-tag = [ weak ] opaque-tag
     weak       = %x57.2F ; "W/", case-sensitive
     opaque-tag = DQUOTE *etagc DQUOTE
     etagc      = %x21 / %x23-7E / obs-text ; VCHAR except double quotes, plus obs-text
   */
  def EntityTag: Rule1[headers.ETag.EntityTag] = {
    def weak: Rule1[Boolean] = rule { "W/" ~ push(true) | push(false) }

    // obs-text: http://tools.ietf.org/html/rfc7230#section-3.2.6
    def obsText: Rule0 = rule { "\u0080" - "\u00FF" }

    def etagc: Rule0 = rule { "\u0021" | "\u0023" - "\u007e" | obsText }

    def opaqueTag: Rule1[String] = rule { '"' ~ capture(zeroOrMore(etagc)) ~ '"' }

    rule {
      weak ~ opaqueTag ~> { (weak: Boolean, tag: String) =>
        headers.ETag.EntityTag(tag, weak)
      }
    }
  }
  // scalastyle:on public.methods.have.type
}

private[http4s] object AdditionalRules {
  def httpDate(s: String): ParseResult[HttpDate] =
    new Parser with AdditionalRules {
      override def input: ParserInput = s
    }.HttpDate
      .run()(Parser.DeliveryScheme.Either)
      .leftMap(e => ParseFailure("Invalid HTTP date", e.format(s)))
}
