/*
 * Derived from https://github.com/spray/spray/blob/v1.1-M7/spray-http/src/main/scala/spray/http/parser/AdditionalRules.scala
 *
 * Copyright (C) 2011-2012 spray.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.http4s
package parser

import org.parboiled2._
import scala.util.Try
import shapeless.{HNil, ::}
import java.net.InetAddress

private[parser] trait AdditionalRules extends Rfc2616BasicRules { this: Parser =>
  
  def EOL: Rule0 = rule { OptWS ~ EOI }  // Strip trailing whitespace

  def Digits: Rule1[String] = rule { capture(zeroOrMore( Digit )) }

  def Value = rule { Token | QuotedString }

  def Parameter: Rule1[(String,String)] = rule { Token ~ "=" ~ OptWS ~ Value ~> ((_: String, _: String)) }

  def HttpDate: Rule1[DateTime] = rule { (RFC1123Date | RFC850Date | ASCTimeDate) }

  def RFC1123Date: Rule1[DateTime] = rule {
    // TODO: hopefully parboiled2 will get more helpers so we don't need to chain methods to get under 5 args
  Wkday ~ str(", ") ~ Date1 ~ ch(' ') ~ Time ~ ch(' ') ~ ("GMT" | "UTC") ~> {
    (year: Int, hour: Int, min: Int, sec: Int) =>
            createDateTime(year, _:Int, _:Int, hour, min, sec, _:Int)
        } ~> {
          (wkday: Int, day: Int, month: Int, f: Function3[Int, Int, Int, DateTime]) =>
            f(month, day, wkday)
        }
  }

  def RFC850Date: Rule1[DateTime] = rule {
    // TODO: hopefully parboiled2 will get more helpers so we don't need to chain methods to get under 5 args
    Weekday ~ str(", ") ~ Date2 ~ ch(' ') ~ Time ~ ch(' ') ~ ("GMT" | "UTC") ~> {
      (year: Int, hour: Int, min: Int, sec: Int) =>
        createDateTime(year, _:Int, _:Int, hour, min, sec, _:Int)
    } ~> {
      (wkday: Int, day: Int, month: Int, f: Function3[Int, Int, Int, DateTime]) =>
        f(month, day, wkday)
    }
  }

  def ASCTimeDate: Rule1[DateTime] = rule {
    Wkday ~ ch(' ') ~ Date3 ~ ch(' ') ~ Time ~ ch(' ') ~ Digit4 ~> {
      (hour:Int, min:Int, sec:Int, year:Int) =>
        createDateTime(year, _:Int, _:Int, hour, min, sec, _:Int)
      } ~> { (wkday:Int, month:Int, day:Int, f: (Int, Int, Int) => DateTime) =>
        f(month, day, wkday)
      }
  }

  def Date1: RuleN[Int::Int::Int::HNil] = rule { Digit2 ~ ch(' ') ~ Month ~ ch(' ') ~ Digit4 }

  def Date2: RuleN[Int::Int::Int::HNil] = rule { Digit2 ~ ch('-') ~ Month ~ ch('-') ~ Digit4 }

  def Date3: Rule2[Int, Int] = rule { Month ~ ch(' ') ~ (Digit2 | ch(' ') ~ Digit1) }

  def Time: RuleN[Int::Int::Int::HNil] = rule { Digit2 ~ ch(':') ~ Digit2 ~ ch(':') ~ Digit2 }

  def Wkday: Rule1[Int] = rule { ("Sun" ~ push(0)) |
                                 ("Mon" ~ push(1)) |
                                 ("Tue" ~ push(2)) |
                                 ("Wed" ~ push(3)) |
                                 ("Thu" ~ push(4)) |
                                 ("Fri" ~ push(5)) |
                                 ("Sat" ~ push(6)) }

  def Weekday: Rule1[Int] = rule { ("Sunday"   ~ push(0)) |
                                   ("Monday"   ~ push(1)) |
                                   ("Tuesday"  ~ push(2)) |
                                   ("Wedsday"  ~ push(3)) |
                                   ("Thursday" ~ push(4)) |
                                   ("Friday"   ~ push(5)) |
                                   ("Saturday" ~ push(6)) }

  def Month: Rule1[Int] = rule {  ("Jan" ~ push(1))  |
                                  ("Feb" ~ push(2))  |
                                  ("Mar" ~ push(3))  |
                                  ("Apr" ~ push(4))  |
                                  ("May" ~ push(5))  |
                                  ("Jun" ~ push(6))  |
                                  ("Jul" ~ push(7))  |
                                  ("Aug" ~ push(8))  |
                                  ("Sep" ~ push(9))  |
                                  ("Oct" ~ push(10)) |
                                  ("Nov" ~ push(11)) |
                                  ("Dec" ~ push(12)) }

  def Digit1: Rule1[Int] = rule { capture(Digit) ~> {s: String => s.toInt} }

  def Digit2: Rule1[Int] = rule { capture(Digit ~ Digit) ~> {s: String => s.toInt} }

  def Digit3: Rule1[Int] = rule { capture(Digit ~ Digit ~ Digit) ~> {s: String => s.toInt} }

  def Digit4: Rule1[Int] = rule { capture(Digit ~ Digit ~ Digit ~ Digit) ~> {s: String => s.toInt} }

  def Ip4Number = rule { Digit3 | Digit2 | Digit1 }

  def Ip: Rule1[InetAddress] = rule {
    Ip4Number ~ ch('.') ~ Ip4Number ~ ch('.') ~ Ip4Number ~ ch('.') ~ Ip4Number  ~ OptWS ~>
    { (a:Int,b:Int,c:Int,d:Int) => InetAddress.getByAddress(Array(a.toByte, b.toByte, c.toByte, d.toByte)) }
  }

  private def createDateTime(year: Int, month: Int, day: Int, hour: Int, min: Int, sec: Int, wkday: Int) = {
    Try(DateTime(year, month, day, hour, min, sec)).getOrElse {
      // TODO Would be better if this message had the real input.
      throw new Exception("Invalid date: "+year+"-"+month+"-"+day+" "+hour+":"+min+":"+sec )
    }
  }

  /* 3.9 Quality Values */

  def QValue: Rule1[QValue] = rule {
    // more loose than the spec which only allows 1 to max. 3 digits/zeros
    (capture(ch('0') ~ optional(ch('.') ~ oneOrMore(Digit))) ~>
      (org.http4s.QValue.fromString(_).valueOr(e => throw new ParseException(e.copy(sanitized = "Invalid q-value"))))) |
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
      weak ~ opaqueTag ~> { (weak: Boolean, tag: String) => headers.ETag.EntityTag(tag, weak) }
    }
  }

}