/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s.internal.parsing

import cats.data.NonEmptyList
import cats.parse.{Parser, Parser1}
import cats.parse.Parser.{char, charIn, length1, rep, string1}
import cats.parse.Rfc5234.{alpha, digit, hexdig, htab, sp}
import org.http4s.Uri.Ipv6Address

import scala.Predef.->

/** Common rules defined in Rfc3986
  *
  * @see [[https://tools.ietf.org/html/rfc3986]]
  */
object Rfc3986 {

  val ipv6: Parser1[Ipv6Address] = {

    val h16: Parser1[Short] = {
      (hexdig ~ hexdig ~ hexdig ~ hexdig).string.map { (s: String) => java.lang.Integer.parseInt(s, 16).toShort }
    }

    /*
    def DecOctet =
    rule {
      "1" ~ Digit ~ Digit |
        "2" ~ ("0" - "4") ~ Digit |
        "25" ~ ("0" - "5") |
        ("1" - "9") ~ Digit |
        Digit
    }
     */

    val decOctet = (char('1') ~ digit ~ digit)
      .orElse1(char('2') ~ charIn('0', '4') ~ digit)
      .orElse1(string1("25") ~ charIn('0', '5'))
      .orElse1(charIn('1', '9') ~ digit)
      .orElse1(digit).string.map(_.toInt.toByte)

    val ls32: Parser1[Short] = {
      val option1 = h16 ~ char(':') ~ h16
      val dot = char('.')
      val option2: Parser1[(Short, Short)] = (decOctet ~ dot ~ decOctet ~ dot ~ decOctet ~ dot ~ decOctet).map {
        case ((((((a, _), b), _), c), _), d) =>
          ((a << 8) | b).toShort -> ((c << 8) | d).toShort
      }
      option1.orElse1(option2)
    }
  }

}
